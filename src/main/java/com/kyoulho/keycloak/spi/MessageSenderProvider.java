package com.kyoulho.keycloak.spi;

import org.keycloak.provider.Provider;
import java.util.Map;

public interface MessageSenderProvider extends Provider {
    // Send simple text message (e.g. SMS)
    void sendMessage(String to, String title, String content) throws Exception;

    // Send template message (e.g. AlimTalk) with fallback to SMS
    // params: key-value pairs for template placeholders
    void sendTemplateMessage(String to, String templateId, String title, String content, java.util.Map<String, String> params) throws Exception;
}
