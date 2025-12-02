package com.kyoulho.keycloak.otp;

public enum ErrorCode {
    INVALID_REQUEST("요청 값이 올바르지 않습니다."),
    CLIENT_NOT_FOUND("클라이언트를 찾을 수 없습니다."),
    AUTH_SESSION_NOT_FOUND("인증 세션을 찾을 수 없습니다. 화면을 새로고침 후 다시 시도하세요."),
    INVALID_PHONE("휴대폰 번호 형식이 올바르지 않습니다."),
    CONFIG_NOT_FOUND("관리자 설정을 찾을 수 없습니다. 관리자에게 문의하세요."),
    SEND_FAILED("인증번호 발송에 실패했습니다. 관리자에게 문의하세요."),
    NO_OTP("발송된 인증번호가 없습니다. 먼저 인증번호를 요청하세요."),
    EXPIRED("인증번호의 유효시간이 지났습니다. 다시 발송해 주세요."),
    MISMATCH("인증번호가 일치하지 않습니다."),
    ;
    private final String message;

    ErrorCode(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
