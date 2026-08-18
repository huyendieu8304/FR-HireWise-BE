package com.hirewise.be.exception;

/**
 * RBAC layer 1 (Authentication Freshness, BR-AUTH-07): JWT hop le nhung
 * users.status khac ACTIVE tai thoi diem xu ly request, hoac chua co ban
 * ghi noi bo tuong ung (chua duoc HR Admin tao qua USER_CREATE)
 */
public class AccountNotActiveException extends ForbiddenActionException {
    public AccountNotActiveException() {
        super(ErrorCode.FORBIDDEN);
    }
}
