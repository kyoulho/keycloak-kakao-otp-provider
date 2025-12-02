package com.kyoulho.keycloak.spi.impl;

import com.kyoulho.keycloak.spi.MessageSenderProvider;
import com.kyoulho.keycloak.spi.MessageSenderProviderFactory;
import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

import java.util.Map;

public class BiztalkMessageSenderProviderFactory implements MessageSenderProviderFactory {
    public static final String ID = "biztalk";
    
    private Map<String, String> globalConfig;

    @Override
    public MessageSenderProvider create(KeycloakSession session) {
        return new BiztalkMessageSenderProvider(globalConfig);
    }

    @Override
    public void init(Config.Scope config) {
        // Load from SPI configuration (Environment variables mapped to SPI config)
        // e.g. KC_SPI_MESSAGE_SENDER_BIZTALK_BASE_URL -> config.get("baseUrl")
        globalConfig = new java.util.HashMap<>();
        globalConfig.put("baseUrl", config.get("baseUrl"));
        globalConfig.put("systemKey", config.get("systemKey"));
        globalConfig.put("projectId", config.get("projectId"));
        globalConfig.put("callBackNo", config.get("callBackNo"));
        
        // Done Template Config
        globalConfig.put("tmplDone", config.get("tmplDone"));
        globalConfig.put("titleDone", config.get("titleDone"));
        globalConfig.put("contentDone", config.get("contentDone"));
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
