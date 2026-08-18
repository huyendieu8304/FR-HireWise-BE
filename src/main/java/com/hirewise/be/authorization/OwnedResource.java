package com.hirewise.be.authorization;

/**
 * Record chứa thông tin tối thiểu của 1 tài nguyên sau khi được load từ DB.
 *
 * Thiết kế này gom toàn bộ dữ liệu cần thiết cho cả Layer 3 (Access Scope) và Layer 4 (Ownership)
 * vào một nơi, đảm bảo chỉ cần TRUY VẤN DATABASE 1 LẦN DUY NHẤT.
 *
 * @param ownerId      ID của người sở hữu tài nguyên (null nếu chưa có ai sở hữu).
 * @param departmentId ID của phòng ban quản lý tài nguyên này (dùng cho Layer 3).
 * @param jobId        ID của Job liên quan đến tài nguyên này (dùng cho Layer 3).
 */
public record OwnedResource(Long ownerId, Long departmentId, Long jobId) {

    /**
     * Chuyển đổi dữ liệu sang ResourceContext để truyền vào AccessControlService (Layer 3).
     */
    public ResourceContext toResourceContext() {
        return new ResourceContext(departmentId, jobId);
    }
}
