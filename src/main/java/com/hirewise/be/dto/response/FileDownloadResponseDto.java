package com.hirewise.be.dto.response;

public record FileDownloadResponseDto(
        String fileName,
        String mimeType,
        byte[] content
) {
}
