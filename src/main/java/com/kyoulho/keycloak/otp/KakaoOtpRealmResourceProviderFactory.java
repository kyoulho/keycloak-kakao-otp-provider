package com.kyoulho.keycloak.otp;

import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.services.resource.RealmResourceProviderFactory;

public class KakaoOtpRealmResourceProviderFactory implements RealmResourceProviderFactory {
    public static final String ID = "kakao-otp";

    @Override public KakaoOtpRealmResourceProvider create(KeycloakSession session) { return new KakaoOtpRealmResourceProvider(session); }
    @Override public void init(Config.Scope config) {}
    @Override public void postInit(KeycloakSessionFactory factory) {}
    @Override public void close() {}
    @Override public String getId() { return ID; }
}
