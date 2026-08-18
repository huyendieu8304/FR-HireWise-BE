package com.hirewise.be.authorization;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Central registry of every {@link OwnershipResolver} in the application.
 * <p>
 * Relies on Spring dependency injection to autowire all beans implementing
 * {@code OwnershipResolver} and indexes them into a map keyed by
 * {@code resourceType}.
 */
@Component
public class OwnershipResolverRegistry {

    private final Map<String, OwnershipResolver> resolversByType;

    /**
     * Spring automatically collects every bean implementing
     * {@code OwnershipResolver} and injects them as {@code List<OwnershipResolver>}.
     *
     * @param resolvers all registered ownership resolver beans
     */
    public OwnershipResolverRegistry(List<OwnershipResolver> resolvers) {
        this.resolversByType = resolvers.stream()
                .collect(Collectors.toMap(OwnershipResolver::resourceType, Function.identity()));
    }

    /**
     * Looks up the resolver registered for {@code resourceType}.
     *
     * @param resourceType the resource type code to look up
     * @return the matching resolver
     * @throws IllegalStateException if no resolver is registered for {@code resourceType}
     */
    public OwnershipResolver get(String resourceType) {
        OwnershipResolver resolver = resolversByType.get(resourceType);
        if (resolver == null) {
            // Fail fast with a clear message so developers immediately know which
            // resourceType is missing a resolver, instead of hitting a NPE later.
            throw new IllegalStateException("Khong co OwnershipResolver nao dang ky cho resourceType=" + resourceType);
        }
        return resolver;
    }
}
