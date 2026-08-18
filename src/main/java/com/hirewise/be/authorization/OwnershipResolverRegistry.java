package com.hirewise.be.authorization;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Registry quản lý tập trung tất cả các Resolver trong ứng dụng.
 *
 * Tận dụng tính năng Dependency Injection của Spring để tự động thu thập (autowire)
 * toàn bộ các bean triển khai `OwnershipResolver` và lưu vào Map theo `resourceType`.
 */
@Component
public class OwnershipResolverRegistry {

    private final Map<String, OwnershipResolver> resolversByType;

    /**
     * Spring sẽ tự động tìm tất cả các Bean implement OwnershipResolver và truyền vào danh sách List<OwnershipResolver>.
     */
    public OwnershipResolverRegistry(List<OwnershipResolver> resolvers) {
        this.resolversByType = resolvers.stream()
                .collect(Collectors.toMap(OwnershipResolver::resourceType, Function.identity()));
    }

    /**
     * Tìm Resolver tương ứng với resourceType. Ném ngoại lệ nếu chưa đăng ký Resolver nào cho type đó.
     */
    public OwnershipResolver get(String resourceType) {
        OwnershipResolver resolver = resolversByType.get(resourceType);
        if (resolver == null) {
            //for dev to read
            throw new IllegalStateException("Khong co OwnershipResolver nao dang ky cho resourceType=" + resourceType);
        }
        return resolver;
    }
}
