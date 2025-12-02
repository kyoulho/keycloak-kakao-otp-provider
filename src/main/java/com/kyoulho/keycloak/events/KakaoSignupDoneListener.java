package com.kyoulho.keycloak.events;

import com.kyoulho.keycloak.spi.MessageSenderProvider;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;



public class KakaoSignupDoneListener implements EventListenerProvider {
    private final KeycloakSession session;
    private final String phoneAttr;
    private final String tmplCode;
    private final String title;
    private final String content;

    public KakaoSignupDoneListener(KeycloakSession session, String phoneAttr, String tmplCode, String title, String content) {
        this.session = session;
        this.phoneAttr = phoneAttr;
        this.tmplCode = tmplCode;
        this.title = title;
        this.content = content;
    }

    // -------- 사용자 스스로 가입(REGISTER) 이벤트 --------
    @Override
    public void onEvent(Event event) {
        if (event == null || event.getType() != EventType.REGISTER) return;

        RealmModel realm = session.realms().getRealm(event.getRealmId());
        if (realm == null) return;

        UserModel user = session.users().getUserById(realm, event.getUserId());
        if (user == null) return;

        sendDoneForUser(session, realm, user);
    }

    // -------- 관리자가 생성한 사용자(Admin 콘솔/REST) --------
    @Override
    public void onEvent(AdminEvent event, boolean includeRepresentation) {
        if (event == null) return;
        if (event.getResourceType() != ResourceType.USER) return;        // 사용자 리소스만
        if (event.getOperationType() != OperationType.CREATE) return;     // 생성일 때만

        RealmModel realm = session.realms().getRealm(event.getRealmId());
        if (realm == null) return;

        // resourcePath 예: "users/{userId}" 또는 "users/{userId}/reset-password"
        String path = event.getResourcePath();
        if (path == null) return;
        String[] parts = path.split("/");
        if (parts.length < 2) return;
        String userId = parts[1];

        UserModel user = session.users().getUserById(realm, userId);
        if (user == null) return;

        sendDoneForUser(session, realm, user);
    }

    // -------- 공통 전송 로직 --------
    private void sendDoneForUser(KeycloakSession session, RealmModel realm, UserModel user) {
        if (tmplCode == null || title == null || content == null) return;

        String phone = user.getFirstAttribute(phoneAttr);
        if (isBlank(phone)) return;

        String name = firstNonBlank(user.getFirstAttribute("name"), user.getUsername());

        try {
            MessageSenderProvider sender = session.getProvider(MessageSenderProvider.class);
            if (sender != null) {
                java.util.Map<String, String> params = new java.util.HashMap<>();
                params.put("name", name);
                params.put("phone", phone);
                
                sender.sendTemplateMessage(phone, tmplCode, title, content, params);
            }
        } catch (Exception ignore) {
            // 알림 실패해도 가입은 계속 진행
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String firstNonBlank(String... v) {
        if (v == null) return null;
        for (String s : v) if (!isBlank(s)) return s;
        return null;
    }

    @Override
    public void close() {
    }
}
