package com.hirewise.be.authorization;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller method as requiring an ownership check (RBAC Layer 4).
 * <p>
 * Applies to resources that ALREADY EXIST in the DB. Instead of manually
 * checking {@code ownerId == currentUser.id} in the service/controller,
 * {@link OwnershipAspect} intercepts the call and enforces all 3 RBAC layers
 * (Layer 2 -> Layer 3 -> Layer 4) automatically before the method runs.
 * <p>
 * REQUIREMENT: the annotated method MUST declare a parameter of type
 * {@code CurrentUser} (typically paired with {@code @CurrentUserPrincipal}).
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresOwnership {

    /**
     * Resource type code (e.g. {@code "JOB_POSITION"}, {@code "APPLICATION"}).
     * Must match the value returned by {@link OwnershipResolver#resourceType()}.
     */
    String resourceType();

    /**
     * Name of the method parameter that carries the resource id (e.g.
     * {@code "id"}, {@code "jobId"}). The aspect uses this name to pull the
     * actual id value out of the arguments at call time.
     */
    String idParam();

    /**
     * Permission code being checked (e.g. {@code "JOB_EDIT"},
     * {@code "APPLICATION_REJECT"}). Used for the Layer 2/3 checks and to
     * look up the ownership policy in {@link OwnershipPolicyRegistry}.
     */
    String permission();
}
