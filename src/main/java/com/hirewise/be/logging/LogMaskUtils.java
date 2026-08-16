package com.hirewise.be.logging;

/**
 * Che (mask) du lieu nhay cam truoc khi dua vao log - dung khi BAT BUOC
 * phai log 1 truong nhay cam de debug (vd email de doi chieu voi ticket
 * ho tro), thay vi log nguyen van.
 *
 * KHONG dung cho: mat khau, token, ma OTP, so the/CVV, ma so dinh danh
 * ca nhan (CCCD/CMND)... nhung gia tri nay KHONG duoc log du da mask hay
 * chua - xem LOGGING_CONVENTION.md muc "Khong ghi log du lieu nhay cam".
 *
 * Vi du:
 *   log.debug("Recruiter email: {}", LogMaskUtils.maskEmail(email));
 */
public final class LogMaskUtils {

    private LogMaskUtils() {
    }

    /**
     * "nguyenvana@gmail.com" -> "ng*******a@gmail.com"
     */
    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return email;
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) {
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
     * "0987654321" -> "098****321"
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
     * Che toan bo, chi giu lai so ky tu cuoi de doi chieu (vd token, id nhay cam).
     * "sk_live_51Hxxxxxxxxxxxxxxxxxx" -> "****************xxxx"
     */
    public static String maskKeepLast(String value, int keepLast) {
        if (value == null) {
            return null;
        }
        if (value.length() <= keepLast) {
            return "*".repeat(value.length());
        }
        return "*".repeat(value.length() - keepLast) + value.substring(value.length() - keepLast);
    }
}
