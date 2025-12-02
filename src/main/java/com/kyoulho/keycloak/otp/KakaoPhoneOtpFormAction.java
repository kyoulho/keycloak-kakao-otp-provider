package com.kyoulho.keycloak.otp;


import org.keycloak.Config;
import org.keycloak.authentication.*;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.*;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;

import java.util.List;

public class KakaoPhoneOtpFormAction implements FormAction, FormActionFactory, ConfigurableAuthenticatorFactory {

    public static final String ID = "kakao-phone-otp";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayType() {
        return "Kakao Phone OTP (BizTalk)";
    }

    @Override
    public String getHelpText() {
        return "카카오 비즈톡 OTP/DONE 템플릿 및 연결 정보 설정용 (동작 없음)";
    }

    @Override
    public String getReferenceCategory() {
        return "kakao-biztalk";
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
        return REQUIREMENT_CHOICES;
    }

    @Override
    public boolean isUserSetupAllowed() {
        return false;
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return ProviderConfigurationBuilder.create()
                .property().name("tmplOtp").type(ProviderConfigProperty.STRING_TYPE).label("tmplOtp").helpText("인증번호 템플릿코드 (선택: 설정 시 기본값 덮어씀)").add()
                .property().name("titleOtp").type(ProviderConfigProperty.STRING_TYPE).label("titleOtp").add()
                .property().name("contentOtp").type(ProviderConfigProperty.STRING_TYPE).label("contentOtp").add()
                .build();
    }

    // --- FormAction (동작 없음: 단순 통과) ---
    @Override
    public void buildPage(FormContext context, LoginFormsProvider form) { /* no-op */ }

    @Override
    public void validate(ValidationContext context) {
        // This action is just for configuration holding or simple pass-through if used in flow
        // The actual sending logic might be in a separate Authenticator or Resource.
        // But wait, if this is an Authenticator Factory, where is the Authenticator?
        // Ah, this class implements FormAction, so it IS the action.
        // But the previous code didn't have any sending logic in validate/success?
        // It seems this class was mainly used to hold configuration for the RealmResourceProvider to look up.
        // Yes, ExecutionConfigLookup finds config by this ID.
        // So we just need to keep the config properties.
        context.success();
    }

    @Override
    public void success(FormContext context) { /* no-op */ }

    @Override
    public boolean requiresUser() {
        return false;
    }

    @Override
    public boolean configuredFor(KeycloakSession s, RealmModel r, UserModel u) {
        return true;
    }

    @Override
    public void setRequiredActions(KeycloakSession s, RealmModel r, UserModel u) { /* no-op */ }

    @Override
    public void close() {
    }

    // --- Factory life-cycle ---
    @Override
    public FormAction create(KeycloakSession session) {
        return this;
    }

    @Override
    public void init(Config.Scope config) {
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
    }
}
