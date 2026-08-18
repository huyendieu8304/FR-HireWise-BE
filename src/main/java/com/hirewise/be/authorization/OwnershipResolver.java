package com.hirewise.be.authorization;

/**
 * Interface định nghĩa cách nạp dữ liệu cho từng loại tài nguyên cụ thể.
 *
 * Mỗi Entity trong hệ thống cần kiểm tra Ownership sẽ tạo một class triển khai Interface này.
 */
public interface OwnershipResolver {

    /**
     * Trả về mã loại tài nguyên duy nhất (ví dụ: "JOB_POSITION").
     * Khớp với RequiresOwnership#resourceType().
     */
    String resourceType();

    /**
     * Query DB để lấy thông tin tài nguyên theo resourceId và đóng gói thành OwnedResource.
     *
     * @param resourceId ID của tài nguyên truyền từ Controller.
     * @return OwnedResource chứa thông tin ownerId, departmentId, jobId.
     */
    OwnedResource resolve(Object resourceId);
}
