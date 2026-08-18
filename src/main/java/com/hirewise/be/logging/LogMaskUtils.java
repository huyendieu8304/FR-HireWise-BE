package com.hirewise.be.logging;

/**
 * Masks sensitive data before it is written to logs.
 * <p>
 * Use this ONLY when a sensitive field genuinely MUST be logged for debugging
 * purposes (e.g. logging an email to cross-reference a support ticket), instead of
 * logging the raw value.
 * <p>
 * Do NOT use this for: passwords, tokens, OTP codes, card numbers/CVV, national ID
 * numbers (CCCD/CMND)... those values must NEVER be logged, masked or not - see the
 * "Do not log sensitive data" section of LOGGING_CONVENTION.md.
 * <p>
 * Example:
 * <pre>{@code
 * log.debug("Recruiter email: {}", LogMaskUtils.maskEmail(email));
 * }</pre>
 */
public final class LogMaskUtils {

    private LogMaskUtils() {
    }

    /**
     * Masks the local part of an email address, keeping only the first two and last
     * one character visible so the value can still be recognized without being fully
     * exposed in logs.
     * <p>
     * Example: {@code "nguyenvana@gmail.com" -> "ng*******a@gmail.com"}
     *
     * @param email email address to mask
     * @return the masked email, or the original value unchanged if it is
     *         {@code null} or blank
     */
    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return email;
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) {
            // Local part is 0-1 character long (or "@" wasn't found) - too short to
            // partially reveal, so mask it entirely instead of leaking the whole value.
            return "***" + email.substring(Math.max(atIndex, 0));
        }
        String localPart = email.substring(0, atIndex);
        String domainPart = email.substring(atIndex);
        String visiblePrefix = localPart.substring(0, Math.min(2, localPart.length()));
        String visibleSuffix = localPart.length() > 2 ? localPart.substring(localPart.length() - 1) : "";
        return visiblePrefix + "*".repeat(Math.max(localPart.length() - visiblePrefix.length() - visibleSuffix.length(), 1))
                + visibleSuffix + domainPart;
    }

    /**
     * Masks a phone number, keeping only the first and last three digits visible.
     * <p>
     * Example: {@code "0987654321" -> "098****321"}
     *
     * @param phone phone number to mask
     * @return the masked phone number; {@code null} if {@code phone} is {@code null},
     *         or {@code "***"} if it is too short (fewer than 6 characters) to mask
     *         while still leaving any digits visible
     */
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 6) {
            return phone == null ? null : "***";
        }
        String prefix = phone.substring(0, 3);
        String suffix = phone.substring(phone.length() - 3);
        return prefix + "*".repeat(phone.length() - 6) + suffix;
    }

    /**
     * Masks a value entirely except for the last {@code keepLast} characters, which
     * are left visible so the value can still be cross-referenced (e.g. a token or
     * other sensitive id).
     * <p>
     * Example: {@code maskKeepLast("sk_live_51Hxxxxxxxxxxxxxxxxxx", 4) -> "****************xxxx"}
     *
     * @param value    value to mask
     * @param keepLast number of trailing characters to leave visible
     * @return the masked value; {@code null} if {@code value} is {@code null}
     */
    public static String maskKeepLast(String value, int keepLast) {
        if (value == null) {
            return null;
        }
        if (value.length() <= keepLast) {
            // Value isn't longer than the visible suffix - mask everything rather
            // than accidentally revealing the whole value.
            return "*".repeat(value.length());
        }
        return "*".repeat(value.length() - keepLast) + value.substring(value.length() - keepLast);
    }
}
