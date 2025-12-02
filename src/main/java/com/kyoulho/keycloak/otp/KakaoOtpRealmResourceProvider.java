package com.kyoulho.keycloak.otp;

import com.kyoulho.keycloak.spi.MessageSenderProvider;
import com.kyoulho.keycloak.support.ExecutionConfigLookup;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.services.managers.AuthenticationSessionManager;
import org.keycloak.sessions.AuthenticationSessionModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;

public class KakaoOtpRealmResourceProvider implements org.keycloak.services.resource.RealmResourceProvider {

    // AuthenticationSession 노트 키
    private static final String NOTE_PHONE = "ka.phone";
    private static final String NOTE_OTP = "ka.otp";
    private static final String NOTE_EXPIRES = "ka.otp.exp";
    private static final String NOTE_VERIFIED = "ka.otp.ok";
    private static final Logger log = LoggerFactory.getLogger(KakaoOtpRealmResourceProvider.class);

    private final KeycloakSession session;

    public KakaoOtpRealmResourceProvider(KeycloakSession session) {
        this.session = session;
    }

    @Override
    public Object getResource() {
        return new Api(session);
    }

    @Override
    public void close() {
    }

    @Path("")
    public static class Api {

        private final KeycloakSession session;
        private final SecureRandom rnd = new SecureRandom();

        public Api(KeycloakSession session) {
            this.session = session;
        }

        @POST
        @Path("check-phone")
        @Consumes(MediaType.APPLICATION_JSON)
        @Produces(MediaType.APPLICATION_JSON)
        public Response checkPhone(CheckPhoneReq req) {
            if (req == null || isBlank(req.phone)) return bad(ErrorCode.INVALID_REQUEST);

            RealmModel realm = session.getContext().getRealm();
            String phone = digits(req.phone);
            if (phone.isEmpty()) return bad(ErrorCode.INVALID_PHONE);

            boolean exists = session.users()
                    .searchForUserByUserAttributeStream(realm, "phone_number", phone)
                    .findAny()
                    .isPresent();

            if (exists) return Response.ok(Map.of("ok", false, "message", "이미 등록된 번호입니다.")).build();
            return Response.ok(Map.of("ok", true)).build();
        }

        @POST
        @Path("send")
        @Consumes(MediaType.APPLICATION_JSON)
        @Produces(MediaType.APPLICATION_JSON)
        public Response send(SendReq req) {
            if (req == null || isBlank(req.phone) || isBlank(req.clientId) || isBlank(req.tabId))
                return bad(ErrorCode.INVALID_REQUEST);

            RealmModel realm = session.getContext().getRealm();
            ClientModel client = session.clients().getClientByClientId(realm, req.clientId);
            if (client == null) return bad(ErrorCode.CLIENT_NOT_FOUND);

            AuthenticationSessionModel auth = getAuthSession(realm, client, req.tabId);
            if (auth == null) return bad(ErrorCode.AUTH_SESSION_NOT_FOUND);

            String phone = digits(req.phone);
            if (phone.isEmpty()) return bad(ErrorCode.INVALID_PHONE);

            // 6자리 OTP
            String code = String.format("%06d", rnd.nextInt(1_000_000));
            long exp = Instant.now().plusSeconds(300).toEpochMilli();

            // 설정 로드
            Map<String, String> cfg = ExecutionConfigLookup.findConfig(realm, KakaoPhoneOtpFormAction.ID);
            if (cfg.isEmpty()) return bad(ErrorCode.CLIENT_NOT_FOUND);

            try {
                MessageSenderProvider sender = session.getProvider(MessageSenderProvider.class);
                if (sender == null) {
                    log.error("MessageSenderProvider not found");
                    return bad(ErrorCode.SEND_FAILED);
                }
                

                
                // Pass config as overrides (only contains tmplOtp, etc. if set)
                // We need to extract template info from cfg or env vars
                String tmplCode = cfg.get("tmplOtp");
                String title = cfg.get("titleOtp");
                String content = cfg.get("contentOtp");
                
                // Fallback to env vars if not set in UI config
                if (tmplCode == null) tmplCode = System.getenv("KC_SPI_MESSAGE_SENDER_BIZTALK_TMPL_OTP");
                if (title == null) title = System.getenv("KC_SPI_MESSAGE_SENDER_BIZTALK_TITLE_OTP");
                if (content == null) content = System.getenv("KC_SPI_MESSAGE_SENDER_BIZTALK_CONTENT_OTP");
                
                if (tmplCode == null || title == null || content == null) {
                    log.error("Missing OTP template configuration");
                    return bad(ErrorCode.SEND_FAILED);
                }

                java.util.Map<String, String> params = new java.util.HashMap<>();
                params.put("code", code);
                
                sender.sendTemplateMessage(phone, tmplCode, title, content, params);

                // 세션 저장
                auth.setAuthNote(NOTE_PHONE, phone);
                auth.setAuthNote(NOTE_OTP, code);
                auth.setAuthNote(NOTE_EXPIRES, Long.toString(exp));
                auth.removeAuthNote(NOTE_VERIFIED);

                return Response.ok(Map.of("ok", true)).build();
            } catch (Exception e) {
                log.error("Biztalk OTP send failed: {}", e.getMessage());
                return bad(ErrorCode.SEND_FAILED);
            }
        }

        @POST
        @Path("verify")
        @Consumes(MediaType.APPLICATION_JSON)
        @Produces(MediaType.APPLICATION_JSON)
        public Response verify(VerifyReq req) {
            if (req == null || isBlank(req.code) || isBlank(req.clientId) || isBlank(req.tabId))
                return bad(ErrorCode.INVALID_REQUEST);

            RealmModel realm = session.getContext().getRealm();
            ClientModel client = session.clients().getClientByClientId(realm, req.clientId);
            if (client == null) return bad(ErrorCode.CLIENT_NOT_FOUND);

            AuthenticationSessionModel auth = getAuthSession(realm, client, req.tabId);
            if (auth == null) return bad(ErrorCode.AUTH_SESSION_NOT_FOUND);

            String expect = auth.getAuthNote(NOTE_OTP);
            String expStr = auth.getAuthNote(NOTE_EXPIRES);
            long now = Instant.now().toEpochMilli();
            if (expect == null || expStr == null) return bad(ErrorCode.NO_OTP);
            if (now > Long.parseLong(expStr)) return bad(ErrorCode.EXPIRED);
            if (!expect.equals(req.code)) return bad(ErrorCode.MISMATCH);

            auth.setAuthNote(NOTE_VERIFIED, "true");
            return Response.ok(Map.of("ok", true)).build();
        }

        // ---- helpers ----
        private AuthenticationSessionModel getAuthSession(RealmModel realm, ClientModel client, String tabId) {
            AuthenticationSessionManager mgr = new AuthenticationSessionManager(session);
            return mgr.getCurrentAuthenticationSession(realm, client, tabId);
        }

        private static String digits(String s) {
            return s == null ? "" : s.replaceAll("\\D+", "");
        }

        private static boolean isBlank(String s) {
            return s == null || s.isBlank();
        }

        private static Response bad(ErrorCode code) {
            return Response.status(400).entity(Map.of("ok", false, "message", code.getMessage())).build();
        }

    }

    // ---- 요청 DTO ----
    public static class SendReq {
        public String phone;
        public String clientId;
        public String tabId;
    }

    public static class VerifyReq {
        public String code;
        public String clientId;
        public String tabId;
    }

    public static class CheckPhoneReq {
        public String phone;
    }

}
