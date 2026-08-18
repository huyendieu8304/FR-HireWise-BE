package com.hirewise.be.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.hirewise.be.domain.User;
import com.hirewise.be.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * BR-AUTH-07: "JWT hop le KHONG duoc xem la du dieu kien truy cap" - moi
 * request phai xac minh users.status = ACTIVE tai thoi diem xu ly, khong
 * chi tai thoi diem token duoc cap. Tra thang DB moi request se cham, nen
 * dung cache Caffeine TTL NGAN (mac dinh 45s, option B trong RBAC Design
 * muc 5.3) - du nhanh de khong lam cham request, va du ngan de 1 tai khoan
 * vua bi Blocked khong the tiep tuc goi API qua lau. Khi HR Admin khoa tai
 * khoan, UserAdminService se evict() ngay entry lien quan de khong phai
 * doi het TTL (ket hop voi KeycloakAdminClient#forceLogout - option D).
 */
@Component
public class UserDirectoryService {

    private final UserRepository userRepository;
    private final Cache<String, Optional<UserSnapshot>> cache;

    public UserDirectoryService(UserRepository userRepository,
                                 @Value("${app.rbac.user-status-cache-ttl-seconds:45}") long ttlSeconds) {
        this.userRepository = userRepository;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(ttlSeconds))
                .maximumSize(10_000)
                .build();
    }

    public Optional<UserSnapshot> lookup(String keycloakId) {
        return cache.get(keycloakId, this::loadFromDb);
    }

    public void evict(String keycloakId) {
        cache.invalidate(keycloakId);
    }

    private Optional<UserSnapshot> loadFromDb(String keycloakId) {
        return userRepository.findByKeycloakId(keycloakId).map(this::toSnapshot);
    }

    // Chi con lay userId + status - xem UserSnapshot de biet ly do bo
    // roleCodes/departmentId (khong con noi nao doc lai 2 field do).
    private UserSnapshot toSnapshot(User user) {
        return new UserSnapshot(user.getId(), user.getStatus());
    }
}
