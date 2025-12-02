package com.kyoulho.keycloak.events;

import org.keycloak.Config;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

public class KakaoSignupDoneListenerProviderFactory implements EventListenerProviderFactory {
    public static final String ID = "kakao-signup-done";

    private String phoneAttr = "phone_number";
    private String tmplCode;
    private String title;
    private String content;

    @Override
    public EventListenerProvider create(KeycloakSession session) {
        return new KakaoSignupDoneListener(session, phoneAttr, tmplCode, title, content);
    }

    @Override
    public void init(Config.Scope config) {
        String pa = config.get("phone_attr");
        if (pa != null && !pa.isBlank()) phoneAttr = pa;

        tmplCode = config.get("tmpl_code");
        title = config.get("title");
        content = config.get("content");
        
        // Fallback to env vars if not in SPI config (for backward compatibility or convenience)
        if (tmplCode == null) tmplCode = System.getenv("KC_SPI_MESSAGE_SENDER_BIZTALK_TMPL_DONE");
        if (title == null) title = System.getenv("KC_SPI_MESSAGE_SENDER_BIZTALK_TITLE_DONE");
        if (content == null) content = System.getenv("KC_SPI_MESSAGE_SENDER_BIZTALK_CONTENT_DONE");
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
    }

    @Override
    public void close() {
    }

    @Override
    public String getId() {
        return ID;
    }
}
