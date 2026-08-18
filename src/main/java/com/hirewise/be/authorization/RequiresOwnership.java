package com.hirewise.be.authorization;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation đánh dấu lên phương thức Controller cần kiểm tra quyền sở hữu (RBAC Layer 4).
 *
 * Áp dụng cho các tài nguyên ĐÃ TỒN TẠI trong DB. Thay vì kiểm tra logic "ownerId == currentUser.id"
 * thủ công ở Service/Controller, Aspect sẽ chặn và xử lý tự động toàn bộ 3 lớp RBAC
 * (Layer 2 -> Layer 3 -> Layer 4) trước khi method chạy.
 *
 * YÊU CẦU BẮT BUỘC: Method được đánh dấu PHẢI có 1 tham số kiểu CurrentUser (thường đi kèm @CurrentUserPrincipal).
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresOwnership {

    /**
     * Mã định danh loại tài nguyên (ví dụ: "JOB_POSITION", "APPLICATION").
     * Khớp với giá trị trả về từ OwnershipResolver#resourceType().
     */
    String resourceType();

    /**
     * Tên của tham số trong method chứa ID của tài nguyên (ví dụ: "id", "jobId").
     * Aspect sẽ dùng name này để lấy giá trị ID thực sự truyền vào khi gọi API.
     */
    String idParam();

    /**
     * Mã quyền (Permission Code) đang được kiểm tra (ví dụ: "JOB_EDIT", "APPLICATION_REJECT").
     * Dùng để kiểm tra Layer 2/3 và tra cứu chính sách Ownership trong OwnershipPolicyRegistry.
     */
    String permission();
}
