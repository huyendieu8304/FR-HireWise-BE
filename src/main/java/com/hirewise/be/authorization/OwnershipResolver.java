package com.hirewise.be.authorization;

/**
 * Defines how to load ownership/scope data for a specific resource type.
 * <p>
 * Each entity in the system that needs an ownership check has one
 * implementation of this interface.
 */
public interface OwnershipResolver {

    /**
     * Returns the unique resource type code (e.g. {@code "JOB_POSITION"}).
     * Must match {@link RequiresOwnership#resourceType()}.
     */
    String resourceType();

    /**
     * Queries the DB for the resource identified by {@code resourceId} and
     * packages the result into an {@link OwnedResource}.
     *
     * @param resourceId id of the resource, as passed in from the controller
     * @return the resource's ownerId, departmentId and jobId
     */
    OwnedResource resolve(Object resourceId);
}
