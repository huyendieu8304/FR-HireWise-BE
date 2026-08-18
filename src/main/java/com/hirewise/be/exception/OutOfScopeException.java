package com.hirewise.be.exception;

/**
 * RBAC layer 3 (Access Scope): user co permission nhung resource muc tieu
 * (phong ban/Job) nam ngoai pham vi duoc gan, hoac thieu can_write cho hanh
 * dong ghi. Xem AccountNotActiveException ve ly do dung chung ErrorCode.FORBIDDEN.
 */
public class OutOfScopeException extends ForbiddenActionException {
    public OutOfScopeException() {
        super(ErrorCode.FORBIDDEN);
    }
}
