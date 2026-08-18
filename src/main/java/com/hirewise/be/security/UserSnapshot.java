package com.hirewise.be.security;

import com.hirewise.be.domain.UserStatus;

/**
 * Anh chup GON cua ban ghi users noi bo, duoc RBAC layer 1 (Authentication
 * Freshness) nap 1 lan/request (co cache TTL ngan - xem UserDirectoryService)
 * de AuthenticationFreshnessFilter quyet dinh cho request di tiep hay khong,
 * roi CurrentUserResolver doc lai de lay userId noi bo gan vao CurrentUser.
 *
 *  - status:  RBAC layer 1 (BR-AUTH-07) can de biet tai khoan co con ACTIVE
 *             khong tai thoi diem request, khong chi tai thoi diem cap JWT.
 *  - userId:  id noi bo, dung lam owner id cho RBAC layer 4 (Ownership) va
 *             de service truy van du lieu lien quan toi user nay.
 *
 */
public record UserSnapshot(Long userId, UserStatus status) {
    /** Attribute key luu snapshot vao HttpServletRequest, xem
     * AuthenticationFreshnessFilter va CurrentUserResolver. */
    public static final String REQUEST_ATTRIBUTE = "hirewise.userSnapshot";
}
