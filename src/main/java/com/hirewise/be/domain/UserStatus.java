package com.hirewise.be.domain;

/**
 * Trang thai tai khoan noi bo. BLOCKED/DISABLED phai bi tu choi ngay lap
 * tuc o RBAC layer 1 (Authentication Freshness - BR-AUTH-07), bat ke JWT
 * con hop le hay khong.
 */
public enum UserStatus {
    ACTIVE,
    BLOCKED,
    DISABLED
}
