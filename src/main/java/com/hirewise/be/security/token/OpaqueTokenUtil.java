package com.hirewise.be.security.token;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

/**
 * Helper for the "{@code <id>:<secret>}" opaque token scheme shared by
 * refresh tokens ({@code user_sessions}) and activation links
 * ({@code activation_tokens}).
 * <p>
 * Only the DB row's id is derivable from the raw token (so it can be looked
 * up in O(1) instead of scanning every row and hashing each one); the
 * random {@code secret} half is never stored raw - only its Argon2id hash
 * is persisted, so a leaked/stolen DB row alone can't be replayed as a
 * valid token.
 */
public final class OpaqueTokenUtil {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int SECRET_BYTES = 32; // 256 bits of entropy

    private OpaqueTokenUtil() {
    }

    /** Generates a new high-entropy, URL-safe secret. */
    public static String newSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Builds the raw client-facing token from an id and its secret. */
    public static String encode(UUID id, String secret) {
        return id.toString() + ":" + secret;
    }

    /** Parsed halves of a raw opaque token, see {@link #decode(String)}. */
    public record Parts(UUID id, String secret) {
    }

    /**
     * Splits a raw opaque token back into its id and secret.
     *
     * @return the parsed parts, or {@code null} if {@code raw} is not in the expected format
     */
    public static Parts decode(String raw) {
        if (raw == null) {
            return null;
        }
        int separator = raw.indexOf(':');
        if (separator <= 0 || separator == raw.length() - 1) {
            return null;
        }
        try {
            UUID id = UUID.fromString(raw.substring(0, separator));
            String secret = raw.substring(separator + 1);
            return new Parts(id, secret);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
