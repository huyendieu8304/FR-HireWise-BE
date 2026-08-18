package com.hirewise.be.authorization;

import com.hirewise.be.security.CurrentUser;

/**
 * Deliverable trung tam cho RBAC layer 2 (Role-Permission) + layer 3
 * (Access Scope), tuong ung ham canAccess(user, action, resource) o muc 6
 * cua RBAC Design (phan lop 1 - Authentication Freshness - da duoc
 * AuthenticationFreshnessFilter xu ly truoc do o muc filter chain; phan lop
 * 4 - Ownership - do OwnershipAspect/@RequiresOwnership dam nhiem rieng).
 *
 * Goi truc tiep trong service, ngay sau khi (hoac ngay khi bat dau, doi voi
 * hanh dong tao moi) da biet duoc phong ban/Job muc tieu - vi resource
 * thuong can duoc load truoc de biet no thuoc pham vi nao.
 */
public interface AccessControlService {

    /**
     * @throws com.hirewise.be.exception.PermissionDeniedException neu khong
     *      role nao cua user duoc cap permissionCode
     * @throws com.hirewise.be.exception.OutOfScopeException neu co permission
     *      nhung resource nam ngoai access scope (hoac thieu can_write cho
     *      hanh dong ghi)
     */
    void checkAccess(CurrentUser user, String permissionCode, ResourceContext resource);
}
