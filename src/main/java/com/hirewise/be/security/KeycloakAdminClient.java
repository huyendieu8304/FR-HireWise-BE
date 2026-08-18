package com.hirewise.be.security;

import com.hirewise.be.exception.ErrorCode;
import com.hirewise.be.exception.KeycloakSyncException;
import com.hirewise.be.logging.LogMaskUtils;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Client tương tác với Keycloak Admin REST API, dùng thư viện chính thức
 * {@code org.keycloak:keycloak-admin-client} (thay vì tự gọi RestClient +
 * tự quản lý lấy/làm mới admin access token bằng tay như bản trước) -
 * thư viện này đã bọc sẵn việc lấy token qua Client Credentials grant và
 * tự làm mới khi hết hạn (qua {@code TokenManager} nội bộ của
 * {@link Keycloak}), nên chỉ cần khởi tạo 1 lần và tái sử dụng như
 * singleton bean (an toàn cho nhiều thread).
 *
 * Dùng 1 Service Account riêng (client credentials grant, role
 * "manage-users" trên realm HireWise - xem setup-keycloak.md mục 2.3),
 * KHÔNG dùng chung client "hirewise-be" (resource server) hay client công
 * khai của FE (xem FE-Keycloak-Setup.md).
 *
 * Cung cấp 4 chức năng chính:
 *  1. createUser / deleteUser           : đồng bộ tạo/xoá user (UC-02).
 *  2. forceLogout                       : đăng xuất bắt buộc (best-effort).
 *  3. assignRealmRole / revokeRealmRole : đồng bộ gán/thu hồi Role (UC-03).
 */
@Slf4j
@Component
public class KeycloakAdminClient {

    /** requiredAction đánh dấu tài khoản chưa có mật khẩu - Keycloak sẽ bắt
     * user đặt mật khẩu ở lần đăng nhập đầu, dùng làm cơ chế "kích hoạt"
     * (EM-01) khi kết hợp với {@code executeActionsEmail}. */
    private static final List<String> ACTIVATION_REQUIRED_ACTIONS = List.of("UPDATE_PASSWORD");

    private final Keycloak adminClient;
    private final String realm;
    private final boolean configured;

    public KeycloakAdminClient(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
            @Value("${app.keycloak.admin.client-id:}") String adminClientId,
            @Value("${app.keycloak.admin.client-secret:}") String adminClientSecret) {
        String realmBaseUrl = issuerUri.substring(0, issuerUri.indexOf("/realms/"));
        this.realm = issuerUri.substring(issuerUri.indexOf("/realms/") + "/realms/".length());
        this.configured = !adminClientId.isBlank() && !adminClientSecret.isBlank();

        // KeycloakBuilder.build() KHONG tu goi network - viec xin access token
        // (va tu lam moi khi het han) chi xay ra "lazily" o lan goi API dau
        // tien, qua TokenManager noi bo cua thu vien. Vi vay an toan de build
        // 1 lan duy nhat o day du app.keycloak.admin.* co dang trong hay khong
        // (method nao thuc su can goi Keycloak deu tu kiem tra `configured`
        // truoc, xem cac method ben duoi).
        this.adminClient = KeycloakBuilder.builder()
                .serverUrl(realmBaseUrl)
                .realm(realm)
                .clientId(configured ? adminClientId : "unconfigured")
                .clientSecret(adminClientSecret)
                .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                .build();
    }

    private RealmResource realmResource() {
        return adminClient.realm(realm);
    }

    /**
     * UC-02: Tạo user mới trên Keycloak (HR Admin thêm nhân sự).
     *
     * Đây là thao tác BẮT BUỘC đồng bộ (khác với forceLogout) - nếu thất
     * bại, PHẢI ném {@link KeycloakSyncException} để {@code UserAdminService}
     * rollback transaction, không được ghi bản ghi {@code users} nội bộ nào
     * khi chưa chắc chắn tài khoản Keycloak đã tồn tại.
     *
     * requiredActions=UPDATE_PASSWORD khiến Keycloak coi đây là tài khoản
     * chưa có mật khẩu; gọi tiếp {@code executeActionsEmail} để Keycloak tự
     * gửi email đặt mật khẩu lần đầu (đóng vai trò email kích hoạt EM-01,
     * dùng SMTP đã cấu hình sẵn ở Keycloak - HireWise-BE không cần tự cài
     * SMTP riêng cho luồng này).
     *
     * @return keycloakId (UUID) của user vừa tạo, dùng làm {@code users.keycloak_id}
     */
    public String createUser(String email, String fullName) {
        if (!configured) {
            log.error("Keycloak admin client chua duoc cau hinh (app.keycloak.admin.client-id/secret) - "
                    + "khong the tao user Keycloak cho email={}", LogMaskUtils.maskEmail(email));
            throw new KeycloakSyncException(ErrorCode.KEYCLOAK_USER_SYNC_FAILED, email);
        }

        UsersResource usersResource = realmResource().users();

        UserRepresentation newUser = new UserRepresentation();
        newUser.setUsername(email);
        newUser.setEmail(email);
        newUser.setFirstName(fullName);
        newUser.setEnabled(true);
        newUser.setEmailVerified(false);
        newUser.setRequiredActions(ACTIVATION_REQUIRED_ACTIONS);

        try (Response response = usersResource.create(newUser)) {
            if (response.getStatus() != Response.Status.CREATED.getStatusCode()) {
                log.error("Tao user Keycloak that bai cho email={}: HTTP {}",
                        LogMaskUtils.maskEmail(email), response.getStatus());
                throw new KeycloakSyncException(ErrorCode.KEYCLOAK_USER_SYNC_FAILED, email);
            }
            String keycloakId = CreatedResponseUtil.getCreatedId(response);
            triggerActivationEmail(usersResource, keycloakId);
            return keycloakId;
        } catch (KeycloakSyncException e) {
            throw e;
        } catch (Exception e) {
            log.error("Tao user Keycloak that bai cho email={}: {}", LogMaskUtils.maskEmail(email), e.getMessage());
            throw new KeycloakSyncException(ErrorCode.KEYCLOAK_USER_SYNC_FAILED, email);
        }
    }

    /**
     * Kích hoạt Keycloak tự gửi email "đặt mật khẩu lần đầu" (EM-01) cho
     * user vừa tạo. Best-effort RIÊNG với createUser(): nếu bước này lỗi
     * (vd realm chưa cấu hình SMTP), user Keycloak vẫn đã được tạo hợp lệ -
     * HR Admin có thể bấm "Gửi lại email kích hoạt" thủ công trên Admin
     * Console sau, không cần rollback cả việc tạo user.
     */
    private void triggerActivationEmail(UsersResource usersResource, String keycloakId) {
        try {
            usersResource.get(keycloakId).executeActionsEmail(ACTIVATION_REQUIRED_ACTIONS);
        } catch (Exception e) {
            log.warn("Gui email kich hoat (EM-01) that bai cho keycloakUserId={}: {}", keycloakId, e.getMessage());
        }
    }

    /**
     * Compensating action cho createUser(): xoá user "mồ côi" bên Keycloak
     * nếu bước ghi DB nội bộ ngay sau đó thất bại (vd DB tạm gián đoạn) -
     * xem {@code UserAdminService#create}. Best-effort - chỉ log lỗi, KHÔNG
     * ném exception (tránh che mất exception gốc đã gây ra rollback).
     */
    public void deleteUser(String keycloakUserId) {
        if (!configured || keycloakUserId == null) {
            return;
        }
        try {
            // Dung UserResource#remove() (goi tren tung user, tra ve void) thay
            // vi UsersResource#delete(id) - on dinh giua cac phien ban thu vien
            // hon vi khong phu thuoc kieu tra ve (Response vs void).
            realmResource().users().get(keycloakUserId).remove();
        } catch (Exception e) {
            log.error("Compensate: xoa user Keycloak 'mo coi' that bai cho keycloakUserId={} - "
                    + "CAN DON THU CONG tren Admin Console: {}", keycloakUserId, e.getMessage());
        }
    }

    /**
     * Bắt buộc huỷ toàn bộ Session đang hoạt động của user trên Keycloak
     * (thu hồi Token - BR-AUTH-04).
     *
     * Lưu ý: đây là cơ chế phòng vệ BỔ SUNG theo chính sách Best-effort.
     * Nếu gặp lỗi (Keycloak ngắt kết nối, thiếu config...), chỉ ghi log
     * cảnh báo chứ KHÔNG ném exception, để không làm gián đoạn luồng khoá
     * tài khoản chính (users.status đã được set BLOCKED/DISABLED thành
     * công ở DB nội bộ rồi - đó mới là nguồn sự thật cho BR-AUTH-07).
     */
    public void forceLogout(String keycloakUserId) {
        if (!configured) {
            log.warn("Keycloak admin client chua duoc cau hinh (app.keycloak.admin.client-id/secret) - "
                    + "bo qua force-logout cho keycloakUserId={}", keycloakUserId);
            return;
        }
        try {
            realmResource().users().get(keycloakUserId).logout();
        } catch (Exception e) {
            log.warn("Force-logout Keycloak that bai cho keycloakUserId={}: {}", keycloakUserId, e.getMessage());
        }
    }

    /**
     * UC-03: Gán Realm Role trên Keycloak cho user (đồng bộ với phân quyền
     * hệ thống nội bộ). Bắt buộc đồng bộ - nếu thất bại, PHẢI ném
     * KeycloakSyncException để Rollback giao dịch ghi DB nội bộ (xem
     * {@code RoleAssignmentService#assignRole}).
     */
    public void assignRealmRole(String keycloakUserId, String roleCode) {
        if (!configured) {
            log.error("Keycloak admin client chua duoc cau hinh - khong the gan role '{}' "
                    + "that ben Keycloak cho keycloakUserId={}", roleCode, keycloakUserId);
            throw new KeycloakSyncException(ErrorCode.KEYCLOAK_ROLE_SYNC_FAILED, roleCode);
        }
        try {
            RoleRepresentation role = fetchRealmRole(roleCode);
            realmResource().users().get(keycloakUserId).roles().realmLevel().add(List.of(role));
        } catch (KeycloakSyncException e) {
            throw e;
        } catch (Exception e) {
            log.error("Gan realm role '{}' that bai cho keycloakUserId={}: {}", roleCode, keycloakUserId, e.getMessage());
            throw new KeycloakSyncException(ErrorCode.KEYCLOAK_ROLE_SYNC_FAILED, roleCode);
        }
    }

    /**
     * UC-03/AF-01: Thu hồi (gỡ) 1 Realm Role đã gán cho user trên Keycloak -
     * đối ngẫu với {@link #assignRealmRole}. Cũng là thao tác BẮT BUỘC đồng
     * bộ: nếu thất bại, DB nội bộ không được coi như đã thu hồi xong (tránh
     * tình trạng DB nói "hết quyền" trong khi JWT lần đăng nhập sau của
     * user vẫn còn role cũ do Keycloak chưa gỡ) - xem
     * {@code RoleAssignmentService#revokeRole}.
     */
    public void revokeRealmRole(String keycloakUserId, String roleCode) {
        if (!configured) {
            log.error("Keycloak admin client chua duoc cau hinh - khong the thu hoi role '{}' "
                    + "that ben Keycloak cho keycloakUserId={}", roleCode, keycloakUserId);
            throw new KeycloakSyncException(ErrorCode.KEYCLOAK_ROLE_SYNC_FAILED, roleCode);
        }
        try {
            RoleRepresentation role = fetchRealmRole(roleCode);
            realmResource().users().get(keycloakUserId).roles().realmLevel().remove(List.of(role));
        } catch (KeycloakSyncException e) {
            throw e;
        } catch (Exception e) {
            log.error("Thu hoi realm role '{}' that bai cho keycloakUserId={}: {}", roleCode, keycloakUserId, e.getMessage());
            throw new KeycloakSyncException(ErrorCode.KEYCLOAK_ROLE_SYNC_FAILED, roleCode);
        }
    }

    /**
     * Tra cứu Role Representation (chứa id nội bộ của Keycloak cho role đó)
     * theo tên Role - dùng chung cho cả assign lẫn revoke.
     */
    private RoleRepresentation fetchRealmRole(String roleCode) {
        try {
            return realmResource().roles().get(roleCode).toRepresentation();
        } catch (NotFoundException e) {
            log.error("Khong tim thay realm role '{}' ben Keycloak - can tao role nay truoc "
                    + "(xem setup-keycloak.md muc 2.2)", roleCode);
            throw new KeycloakSyncException(ErrorCode.KEYCLOAK_ROLE_SYNC_FAILED, roleCode);
        }
    }
}
