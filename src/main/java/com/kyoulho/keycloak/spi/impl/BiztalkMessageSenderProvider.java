package com.kyoulho.keycloak.spi.impl;

import com.kyoulho.keycloak.spi.MessageSenderProvider;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.hc.core5.util.TimeValue;
import org.keycloak.util.JsonSerialization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class BiztalkMessageSenderProvider implements MessageSenderProvider {
    private static final Logger log = LoggerFactory.getLogger(BiztalkMessageSenderProvider.class);
    private static final CloseableHttpClient HTTP;
    
    private final Map<String, String> globalConfig;

    static {
        HTTP = HttpClients.custom()
                .setDefaultRequestConfig(defaultRc())
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setMaxConnTotal(100)
                        .setMaxConnPerRoute(20)
                        .build())
                .evictIdleConnections(TimeValue.of(Duration.ofSeconds(30)))
                .build();
    }

    public BiztalkMessageSenderProvider(Map<String, String> globalConfig) {
        this.globalConfig = globalConfig != null ? globalConfig : Map.of();
    }

    private static RequestConfig defaultRc() {
        return RequestConfig.custom()
                .setConnectionRequestTimeout(10_000, TimeUnit.MILLISECONDS)
                .setResponseTimeout(10_000, TimeUnit.MILLISECONDS)
                .build();
    }

    @Override
    public void close() {
    }

    @Override
    public void sendMessage(String to, String title, String content) throws Exception {
        Map<String, String> sms = baseSmsParams(globalConfig, to, content);
        sendSms(globalConfig, sms);
    }

    @Override
    public void sendTemplateMessage(String to, String templateId, String title, String content, Map<String, String> params) throws Exception {
        // Replace placeholders in content with params
        String finalContent = content;
        if (params != null) {
            for (Map.Entry<String, String> entry : params.entrySet()) {
                String key = "${" + entry.getKey() + "}";
                if (finalContent.contains(key)) {
                    finalContent = finalContent.replace(key, entry.getValue());
                }
            }
        }

        Map<String, String> kakao = baseKakaoParams(globalConfig, to, templateId, title, finalContent);
        
        // BizTalk specific: paramNum and param1..N for buttons or extra data?
        // For now, we assume simple templates. If buttons are needed, we might need a more complex params structure.
        // But based on previous code, OTP used paramNum=1, param1=code.
        // Let's check if params contains "code" (for OTP) or other specific keys if we want to support buttons generically.
        // Or we can just pass all params as button params?
        // The previous implementation for OTP set paramNum=1 and param1=code.
        // Let's try to be smart: if params has "code", we treat it as OTP button param?
        // Ideally, the caller should pass button params explicitly.
        // For this refactoring, let's look for "button_1" etc in params?
        // Or simpler: just pass "code" as param1 if present, for backward compatibility with the logic we had.
        
        if (params != null && params.containsKey("code")) {
            kakao.put("paramNum", "1");
            kakao.put("param1", params.get("code"));
        } else {
            kakao.put("paramNum", "0");
        }

        // SMS Fallback content
        // If it's OTP, we append the code.
        String smsContent = finalContent;
        if (params != null && params.containsKey("code")) {
             String code = params.get("code");
             if (!smsContent.contains(code)) {
                 smsContent += " (인증번호: " + code + ")";
             }
        }

        Map<String, String> sms = baseSmsParams(globalConfig, to, smsContent);

        callWithFallback(globalConfig, kakao, sms);
    }

    // --- Internal Logic ---

    private static String req(String v, String name) {
        if (v == null || v.isBlank()) throw new IllegalArgumentException("Missing config: " + name);
        return v;
    }

    private static final HttpClientResponseHandler<Void> RESPONSE_HANDLER = resp -> {
        int code = resp.getCode();
        if (code / 100 != 2) throw new IllegalStateException("respCode=" + code);
        String body = resp.getEntity() != null ? EntityUtils.toString(resp.getEntity(), StandardCharsets.UTF_8) : "";
        Map<?, ?> json;
        try {
            json = JsonSerialization.readValue(body, Map.class);
        } catch (Exception e) {
            throw new IllegalStateException("invalid json: " + e.getMessage());
        }
        String retCode = json.get("retCode") != null ? String.valueOf(json.get("retCode")) : null;
        if (!"OK".equalsIgnoreCase(retCode)) {
            String retMsg = json.get("retMessage") != null ? String.valueOf(json.get("retMessage")) : null;
            throw new IllegalStateException("retCode=" + retCode + " retMessage=" + retMsg);
        }
        return null;
    };

    private URI buildUri(Map<String, String> config, String path, Map<String, String> params) throws Exception {
        String baseUrl = req(config.get("baseUrl"), "baseUrl");
        URIBuilder b = new URIBuilder(baseUrl + path).setCharset(StandardCharsets.UTF_8);
        for (Map.Entry<String, String> e : params.entrySet()) b.addParameter(e.getKey(), e.getValue());
        return b.build();
    }

    private void callWithFallback(Map<String, String> config, Map<String, String> kakaoParams, Map<String, String> smsParams) throws Exception {
        try {
            sendKakao(config, kakaoParams);
        } catch (Exception first) {
            log.warn("[Biztalk] sendKakao FAIL -> fallback to sendSms. cause={}", first.getMessage());
            sendSms(config, smsParams);
        }
    }

    public void sendKakao(Map<String, String> config, Map<String, String> kakaoParams) throws Exception {
        URI uri = buildUri(config, "/sendKakao", kakaoParams);
        log.info("[Biztalk] sendKakao: {}", uri);
        HTTP.execute(new HttpGet(uri), RESPONSE_HANDLER);
        log.info("[Biztalk] sendKakao Succeeded");
    }

    public void sendSms(Map<String, String> config, Map<String, String> smsParams) throws Exception {
        URI uri = buildUri(config, "/sendSms", smsParams);
        log.info("[Biztalk] sendSms: {}", uri);
        HTTP.execute(new HttpGet(uri), RESPONSE_HANDLER);
        log.info("[Biztalk] sendSms Succeeded");
    }

    private Map<String, String> baseKakaoParams(Map<String, String> config, String toPhone, String tmplCode, String title, String content) {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("sendNo", req(toPhone, "toPhone"));
        p.put("callBackNo", req(config.get("callBackNo"), "callBackNo"));
        p.put("systemKey", req(config.get("systemKey"), "systemKey"));
        p.put("projectId", req(config.get("projectId"), "projectId"));
        p.put("tmplCode", req(tmplCode, "tmplCode"));
        p.put("title", req(title, "title"));
        p.put("content", req(content, "content"));
        return p;
    }

    private Map<String, String> baseSmsParams(Map<String, String> config, String toPhone, String content) {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("sendNo", req(toPhone, "toPhone"));
        p.put("callBackNo", req(config.get("callBackNo"), "callBackNo"));
        p.put("systemKey", req(config.get("systemKey"), "systemKey"));
        p.put("projectId", req(config.get("projectId"), "projectId"));
        p.put("content", req(content, "content"));
        return p;
    }


}
