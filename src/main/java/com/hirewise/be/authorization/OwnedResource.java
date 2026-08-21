package com.hirewise.be.authorization;

import java.util.UUID;

/**
 * Record holding the minimal set of resource data needed after it has been
 * loaded from the DB.
 * <p>
 * Bundles everything required for both Layer 3 (Access Scope) and Layer 4
 * (Ownership) into a single object, ensuring the resource is only queried
 * from the database ONCE.
 *
 * @param ownerId      id of the resource owner ({@code null} if it has no owner yet)
 * @param departmentId id of the department that manages this resource (used for Layer 3)
 * @param jobId        id of the job related to this resource (used for Layer 3)
 */
public record OwnedResource(Long ownerId, Long departmentId, UUID jobId) {

    /**
     * Converts this resource's scope data into a {@link ResourceContext} to
     * pass into {@link AccessControlService} (Layer 3).
     */
    public ResourceContext toResourceContext() {
        return new ResourceContext(departmentId, jobId);
    }
}
