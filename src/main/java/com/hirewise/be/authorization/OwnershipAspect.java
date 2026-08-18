package com.hirewise.be.authorization;

import com.hirewise.be.exception.NotResourceOwnerException;
import com.hirewise.be.security.CurrentUser;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Aspect trung tâm xử lý AOP cho annotation @RequiresOwnership.
 *
 * Tự động chặn các Request vào Controller method có gắn @RequiresOwnership để thực thi đầy đủ
 * 3 lớp phân quyền theo đúng thứ tự chuẩn (BR-RBAC-08):
 *   1. Load tài nguyên từ DB (1 lần duy nhất)
 *   2. Layer 2: Role-Permission Check
 *   3. Layer 3: Access Scope Check
 *   4. Layer 4: Ownership Check
 */
@Aspect
@Component
public class OwnershipAspect {

    private final OwnershipResolverRegistry resolverRegistry;
    private final OwnershipPolicyRegistry policyRegistry;
    private final RolePermissionCache rolePermissionCache;
    private final AccessControlService accessControlService;
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    public OwnershipAspect(OwnershipResolverRegistry resolverRegistry,
                            OwnershipPolicyRegistry policyRegistry,
                            RolePermissionCache rolePermissionCache,
                            AccessControlService accessControlService) {
        this.resolverRegistry = resolverRegistry;
        this.policyRegistry = policyRegistry;
        this.rolePermissionCache = rolePermissionCache;
        this.accessControlService = accessControlService;
    }

    @Around("@annotation(requiresOwnership)")
    public Object enforce(ProceedingJoinPoint joinPoint, RequiresOwnership requiresOwnership) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] paramNames = parameterNameDiscoverer.getParameterNames(signature.getMethod());
        Object[] args = joinPoint.getArgs();

        Object resourceId = null;
        CurrentUser currentUser = null;

        // BƯỚC 1: Trích xuất các tham số từ Controller Method Signature (resourceId & currentUser)
        if (paramNames != null) {
            for (int i = 0; i < paramNames.length; i++) {
                if (paramNames[i].equals(requiresOwnership.idParam())) {
                    resourceId = args[i];
                }
            }
        }
        for (Object arg : args) {
            if (arg instanceof CurrentUser cu) {
                currentUser = cu;
            }
        }

        // Validate tham số bắt buộc
        if (currentUser == null) {
            throw new IllegalStateException(
                    "@RequiresOwnership yeu cau method co 1 tham so CurrentUser (vd @CurrentUserPrincipal): "
                            + signature.toShortString());
        }
        if (resourceId == null) {
            throw new IllegalStateException(
                    "@RequiresOwnership khong tim thay tham so idParam=\"" + requiresOwnership.idParam()
                            + "\" tren " + signature.toShortString());
        }

        // BƯỚC 2: Tìm Resolver phù hợp và Load tài nguyên từ DB (chỉ query 1 lần)
        OwnershipResolver resolver = resolverRegistry.get(requiresOwnership.resourceType());
        OwnedResource resolved = resolver.resolve(resourceId);

        // BƯỚC 3: Thực hiện kiểm tra Layer 2 (Role-Permission) và Layer 3 (Access Scope)
        accessControlService.checkAccess(currentUser, requiresOwnership.permission(), resolved.toResourceContext());

        // BƯỚC 4: Thực hiện kiểm tra Layer 4 (Ownership)
        // 4.1 Lọc ra danh sách các Role của User mà ĐƯỢC CẤP quyền permission này
        Set<String> grantingRoles = currentUser.roles().stream()
                .filter(role -> rolePermissionCache.grants(role, requiresOwnership.permission()))
                .collect(Collectors.toSet());

        // 4.2 Tra cứu chính sách & So sánh ID người sở hữu với ID người dùng hiện tại
        if (policyRegistry.requiresOwnership(requiresOwnership.permission(), grantingRoles)
                && !Objects.equals(resolved.ownerId(), currentUser.userId())) {
            // Ném lỗi 403 / Not Owner
            throw new NotResourceOwnerException();
        }

        // Cho phép Controller Method thực thi tiếp nếu tất cả các bước kiểm tra đều hợp lệ
        return joinPoint.proceed();
    }
}
