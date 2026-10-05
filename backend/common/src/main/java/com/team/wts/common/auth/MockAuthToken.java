package com.team.wts.common.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * MVP용 Mock 인증 토큰. (CLAUDE.md §6.4 – 인증 우선순위 1순위)
 *
 * <p>형식: {@code base64url(userId:expiresAtEpochSecond).base64url(HMAC-SHA256)}
 *
 * <p>서버에 세션을 두지 않는 stateless 토큰이다 (CLAUDE.md §54).
 * JDK에 내장된 {@link Mac}만 사용하므로 JWT 라이브러리를 추가하지 않는다 (CLAUDE.md §14).
 *
 * <p><b>한계</b>: 서명만 검증할 뿐 폐기(revocation) 수단이 없고, 비밀키가 유출되면
 * 임의의 사용자를 위조할 수 있다. 실제 인증은 공개 배포 직전(Phase 11)에 Amazon Cognito로 교체한다 (§62.4).
 */
public final class MockAuthToken {

    private static final String ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final SecretKeySpec key;

    public MockAuthToken(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("인증 토큰 비밀키가 비어 있다.");
        }
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    /** 사용자 ID에 대해 {@code ttl} 동안 유효한 토큰을 발급한다. */
    public String issue(String userId, Duration ttl) {
        if (userId == null || userId.isBlank() || userId.indexOf(':') >= 0) {
            throw new IllegalArgumentException("userId가 비었거나 ':' 를 포함한다: " + userId);
        }
        String payload = userId + ':' + Instant.now().plus(ttl).getEpochSecond();
        String encodedPayload = ENCODER.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return encodedPayload + '.' + ENCODER.encodeToString(sign(encodedPayload));
    }

    /**
     * 토큰을 검증하고 사용자 ID를 돌려준다.
     *
     * <p>서명이 틀렸거나, 형식이 깨졌거나, 만료된 경우 빈 값을 반환한다.
     * 실패 사유를 구분해 노출하지 않는 것은 의도적이다.
     */
    public Optional<String> verify(String token) {
        if (token == null) {
            return Optional.empty();
        }
        int separator = token.indexOf('.');
        if (separator <= 0 || separator == token.length() - 1) {
            return Optional.empty();
        }

        String encodedPayload = token.substring(0, separator);
        byte[] presentedSignature;
        String payload;
        try {
            presentedSignature = DECODER.decode(token.substring(separator + 1));
            payload = new String(DECODER.decode(encodedPayload), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }

        // 타이밍 공격을 피하려고 길이 비교가 아닌 상수 시간 비교를 쓴다.
        if (!MessageDigest.isEqual(sign(encodedPayload), presentedSignature)) {
            return Optional.empty();
        }

        int delimiter = payload.lastIndexOf(':');
        if (delimiter <= 0) {
            return Optional.empty();
        }

        long expiresAt;
        try {
            expiresAt = Long.parseLong(payload.substring(delimiter + 1));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        if (Instant.now().getEpochSecond() >= expiresAt) {
            return Optional.empty();
        }

        return Optional.of(payload.substring(0, delimiter));
    }

    private byte[] sign(String encodedPayload) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return mac.doFinal(encodedPayload.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException e) {
            // HmacSHA256은 모든 JDK에 있고 키도 생성자에서 검증했다. 여기 오면 설정 문제다.
            throw new IllegalStateException("토큰 서명에 실패했다.", e);
        }
    }
}
