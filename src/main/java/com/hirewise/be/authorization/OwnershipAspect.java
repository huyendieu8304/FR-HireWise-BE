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
 * Central AOP aspect implementing the {@link RequiresOwnership} annotation.
 * <p>
 * Intercepts requests to controller methods annotated with
 * {@code @RequiresOwnership} and enforces all 3 authorization layers in the
 * required order (BR-RBAC-08):
 * <ol>
 *   <li>Load the resource from the DB (once only)</li>
 *   <li>Layer 2: Role-Permission check</li>
 *   <li>Layer 3: Access Scope check</li>
 *   <li>Layer 4: Ownership check</li>
 * </ol>
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

    /**
     * Advice that runs around any method annotated with {@code @RequiresOwnership},
     * enforcing Layer 2, Layer 3 and Layer 4 authorization before letting the
     * method proceed.
     *
     * @param joinPoint         the intercepted method invocation
     * @param requiresOwnership the annotation instance present on the method,
     *                          carrying the resource type, id parameter name and
     *                          permission code to check
     * @return the result of the intercepted method if all authorization checks pass
     * @throws IllegalStateException if the intercepted method doesn't declare a
     *      {@code CurrentUser} parameter, or doesn't have a parameter matching
     *      {@code idParam}
     * @throws com.hirewise.be.exception.PermissionDeniedException if Layer 2 denies access
     * @throws com.hirewise.be.exception.OutOfScopeException if Layer 3 denies access
     * @throws NotResourceOwnerException if Layer 4 denies access
     * @throws Throwable whatever the intercepted method itself throws
     */
    @Around("@annotation(requiresOwnership)")
    public Object enforce(ProceedingJoinPoint joinPoint, RequiresOwnership requiresOwnership) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] paramNames = parameterNameDiscoverer.getParameterNames(signature.getMethod());
        Object[] args = joinPoint.getArgs();

        Object resourceId = null;
        CurrentUser currentUser = null;

        // STEP 1: Extract resourceId and currentUser from the controller method's arguments
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

        // Validate the required parameters were found
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

        // STEP 2: Find the matching resolver and load the resource from the DB (single query)
        OwnershipResolver resolver = resolverRegistry.get(requiresOwnership.resourceType());
        OwnedResource resolved = resolver.resolve(resourceId);

        // STEP 3: Run Layer 2 (Role-Permission) and Layer 3 (Access Scope) checks
        accessControlService.checkAccess(currentUser, requiresOwnership.permission(), resolved.toResourceContext());

        // STEP 4: Run the Layer 4 (Ownership) check
        // 4.1 Narrow down to the roles the user has that are actually GRANTED this permission
        Set<String> grantingRoles = currentUser.roles().stream()
                .filter(role -> rolePermissionCache.grants(role, requiresOwnership.permission()))
                .collect(Collectors.toSet());

        // 4.2 Look up the ownership policy and compare the resource owner against the current user;
        // if every granting role requires ownership for this permission and the user isn't the
        // owner, deny access (see OwnershipPolicyRegistry for the "any role wins" rule).
        if (policyRegistry.requiresOwnership(requiresOwnership.permission(), grantingRoles)
                && !Objects.equals(resolved.ownerId(), currentUser.userId())) {
            throw new NotResourceOwnerException();
        }

        // All authorization layers passed -> let the controller method run
        return joinPoint.proceed();
    }
}
