package com.kyoulho.keycloak.spi;

import org.keycloak.provider.Provider;
import org.keycloak.provider.ProviderFactory;
import org.keycloak.provider.Spi;

public class MessageSenderSpi implements Spi {

    @Override
    public boolean isInternal() {
        return false;
    }

    @Override
    public String getName() {
        return "message-sender";
    }

    @Override
    public Class<? extends Provider> getProviderClass() {
        return MessageSenderProvider.class;
    }

    @Override
    public Class<? extends ProviderFactory> getProviderFactoryClass() {
        return MessageSenderProviderFactory.class;
    }
}
