package com.hirewise.be.exception;

/**
 * RBAC layer 2 (Role-Permission): khong co role nao cua user duoc cap
 * permission can thiet. Xem AccountNotActiveException ve ly do dung chung
 * ErrorCode.FORBIDDEN cho client.
 */
public class PermissionDeniedException extends ForbiddenActionException {
    public PermissionDeniedException() {
        super(ErrorCode.FORBIDDEN);
    }
}
