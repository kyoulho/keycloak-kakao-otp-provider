package com.kyoulho.keycloak.support;

import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.AuthenticationFlowModel;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.RealmModel;

import java.util.Map;
import java.util.Objects;

/**
 * 주어진 providerId에 해당하는 실행(Execution)의 AuthenticatorConfig를
 * 플로우 alias에 의존하지 않고, realm 내 모든 top-level flow와 그 하위 flow를 재귀 탐색하여 찾아 반환.
 * - Keycloak 26.x 공개 모델 API만 사용
 */
public final class ExecutionConfigLookup {
    private ExecutionConfigLookup() {}

    /** realm 전체에서 providerId 매칭되는 첫 실행의 config 반환 (없으면 빈 맵) */
    public static Map<String, String> findConfig(RealmModel realm, String providerId) {
        if (realm == null || providerId == null) return Map.of();

        return realm.getAuthenticationFlowsStream()
                .map(flow -> findConfigInFlowRecursively(realm, flow, providerId))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(Map.of());
    }

    /** 특정 flow(및 모든 하위 flow)에서 providerId 실행의 config를 찾아 반환 (없으면 null) */
    private static Map<String, String> findConfigInFlowRecursively(RealmModel realm,
                                                                   AuthenticationFlowModel flow,
                                                                   String providerId) {
        if (flow == null) return null;

        for (AuthenticationExecutionModel exe : (Iterable<AuthenticationExecutionModel>)
                realm.getAuthenticationExecutionsStream(flow.getId())::iterator) {

            if (exe.isAuthenticatorFlow()) {
                // 서브플로우 재귀 탐색
                AuthenticationFlowModel sub = realm.getAuthenticationFlowById(exe.getFlowId());
                Map<String, String> found = findConfigInFlowRecursively(realm, sub, providerId);
                if (found != null) return found;
                continue;
            }

            if (providerId.equals(exe.getAuthenticator())) {
                String cfgId = exe.getAuthenticatorConfig();
                if (cfgId == null) return Map.of(); // 실행은 있으나 config 미설정
                AuthenticatorConfigModel cfg = realm.getAuthenticatorConfigById(cfgId);
                if (cfg == null || cfg.getConfig() == null) return Map.of();
                return cfg.getConfig();
            }
        }
        return null;
    }
}
