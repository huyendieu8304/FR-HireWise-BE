package com.hirewise.be.exception;

/**
 * RBAC layer 4 (Ownership): permission + scope hop le nhung user khong
 * phai chu so huu duoc chi dinh cua resource cu the (vd khong phai
 * recruiter_id cua Job dang sua). Xem AccountNotActiveException ve ly do
 * dung chung ErrorCode.FORBIDDEN.
 */
public class NotResourceOwnerException extends ForbiddenActionException {
    public NotResourceOwnerException() {
        super(ErrorCode.FORBIDDEN);
    }
}
