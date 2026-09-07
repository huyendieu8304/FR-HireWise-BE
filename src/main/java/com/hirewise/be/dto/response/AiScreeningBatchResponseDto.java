package com.hirewise.be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of the "quét cả cột" bulk action: Recruiter bấm 1 nút trên 1 cột
 * Kanban (thường là cột "Mới"/INTAKE) để enqueue AI Screening Run cho MỌI
 * Application CHƯA từng phân tích thành công đang nằm ở đúng Stage đó, thay
 * vì bấm "Phân tích lại" từng ứng viên một. {@code queuedCount}/
 * {@code skippedCount}/{@code alreadyAnalyzedCount} cho FE hiện toast tổng
 * kết ngay (vd "Đã quét 8 hồ sơ, 2 hồ sơ bỏ qua do thiếu CV/không phải PDF,
 * 3 hồ sơ đã có điểm AI từ trước") mà không cần tự đếm lại từ danh sách.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiScreeningBatchResponseDto {
    /** Tổng số Application đang ở Stage này tại thời điểm bấm nút. */
    private int totalApplications;
    /** Số run được queue ở trạng thái PENDING - dispatcher sẽ gọi AI Engine thật. */
    private int queuedCount;
    /** Số run bị đánh FAILED ngay (chưa có CV, hoặc CV không phải .pdf) - không tính vào queuedCount. */
    private int skippedCount;
    /** Số Application đã có điểm AI (aiMatchScore khác null) từ trước - bị bỏ qua, không đụng tới. */
    private int alreadyAnalyzedCount;
}
