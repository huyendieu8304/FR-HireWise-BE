package com.hirewise.be.authorization;

/**
 * Toa do pham vi (phong ban/Job) cua 1 resource cu the, dung cho RBAC layer
 * 3 (Access Scope). `null` nghia la hanh dong khong gan voi 1 phong ban/Job
 * cu the (vd USER_CREATE, ROLE_ASSIGN - quan tri he thong) - khi do
 * AccessControlService bo qua layer 3, chi con layer 2 (role-permission)
 * quyet dinh.
 */
public record ResourceContext(Long departmentId, Long jobId) {

    public static ResourceContext none() {
        return null;
    }

    public static ResourceContext department(Long departmentId) {
        return new ResourceContext(departmentId, null);
    }

    public static ResourceContext job(Long jobId, Long departmentId) {
        return new ResourceContext(departmentId, jobId);
    }
}
