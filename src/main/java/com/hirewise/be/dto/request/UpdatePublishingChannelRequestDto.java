package com.hirewise.be.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * UC-19 step 2: the two things HR Admin may change about a sharing channel.
 * Everything else about a channel (its code, name and share-intent URL) is
 * dictated by the platform and seeded in {@code V39}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePublishingChannelRequestDto {

    /** Bật/tắt kênh — kênh đã tắt không hiện trong modal Chia sẻ (UC-31 EX-01). */
    private boolean enabled;

    /**
     * Giá trị {@code utm_source} gắn vào link chia sẻ. Phải khớp đúng với
     * {@code applications.source} thì UC-32 mới quy được ứng viên về kênh, nên
     * chỉ cho phép ký tự an toàn trên query string.
     */
    @NotBlank(message = "{validation.publishing_channel.utm_source.required}")
    @Size(max = 50, message = "{validation.publishing_channel.utm_source.size}")
    @Pattern(regexp = "^[a-z0-9_-]+$", message = "{validation.publishing_channel.utm_source.pattern}")
    private String utmSource;
}
