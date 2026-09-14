package com.hirewise.be.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hirewise.be.domain.FileStatus;
import com.hirewise.be.domain.StoredFile;
import com.hirewise.be.exception.BadRequestException;
import com.hirewise.be.exception.ErrorCode;
import com.hirewise.be.repository.StoredFileRepository;
import com.hirewise.be.service.EmailAttachment;
import com.hirewise.be.service.EmailService;
import com.hirewise.be.service.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** EM-12: the signed Offer PDF is attached to the email, never linked. */
@ExtendWith(MockitoExtension.class)
class OutboxDispatcherOfferSignedTest {

    private static final Instant NOW = Instant.parse("2026-09-14T00:00:00Z");
    private static final String EMAIL = "nguyenvana@example.com";

    @Mock
    private OutboxEventRepository outboxEventRepository;
    @Mock
    private EmailService emailService;
    @Mock
    private StoredFileRepository storedFileRepository;
    @Mock
    private FileStorageService fileStorageService;

    private OutboxDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new OutboxDispatcher(outboxEventRepository, emailService, storedFileRepository,
                fileStorageService, new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC), 20, 5);
    }

    @Test
    void attachesSignedPdfReadFromStorage() {
        StoredFile file = storedFile();
        when(storedFileRepository.findWithStorageConnectionById(99L)).thenReturn(Optional.of(file));
        when(fileStorageService.readBytes(file)).thenReturn("pdf".getBytes());
        OutboxEvent event = event("{\"email\":\"" + EMAIL + "\",\"jobTitle\":\"Backend\","
                + "\"signedAt\":\"10:00 14/09/2026\",\"startDate\":\"01/10/2026\",\"signedFileId\":99}");

        dispatcher.dispatchOne(event);

        ArgumentCaptor<EmailAttachment> attachment = ArgumentCaptor.forClass(EmailAttachment.class);
        verify(emailService).sendOfferSignedEmail(eq(EMAIL), any(), eq("Backend"), anyString(), any(),
                attachment.capture());
        assertThat(attachment.getValue().fileName()).isEqualTo("Hop-dong-da-ky.pdf");
        assertThat(attachment.getValue().mimeType()).isEqualTo("application/pdf");
        assertThat(attachment.getValue().content()).isEqualTo("pdf".getBytes());
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.SENT);
    }

    @Test
    void unreadablePdf_stillSendsEmailWithoutAttachment() {
        StoredFile file = storedFile();
        when(storedFileRepository.findWithStorageConnectionById(99L)).thenReturn(Optional.of(file));
        when(fileStorageService.readBytes(file))
                .thenThrow(new BadRequestException(ErrorCode.FILE_NOT_YET_AVAILABLE));
        OutboxEvent event = event("{\"email\":\"" + EMAIL + "\",\"jobTitle\":\"Backend\","
                + "\"signedAt\":\"10:00 14/09/2026\",\"signedFileId\":99}");

        dispatcher.dispatchOne(event);

        verify(emailService).sendOfferSignedEmail(eq(EMAIL), any(), eq("Backend"), anyString(), any(), isNull());
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.SENT);
    }

    @Test
    void legacyPayloadWithOnlyALink_sendsEmailWithoutAttachment() {
        OutboxEvent event = event("{\"email\":\"" + EMAIL + "\",\"jobTitle\":\"Backend\","
                + "\"signedAt\":\"10:00 14/09/2026\",\"signedFileLink\":\"https://drive.example/x\"}");

        dispatcher.dispatchOne(event);

        verify(storedFileRepository, never()).findWithStorageConnectionById(any());
        verify(emailService).sendOfferSignedEmail(eq(EMAIL), any(), eq("Backend"), anyString(), any(), isNull());
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.SENT);
    }

    private static OutboxEvent event(String payload) {
        return OutboxEvent.builder()
                .id(1L)
                .eventType(OutboxEventType.OFFER_SIGNED_EMAIL)
                .payload(payload)
                .status(OutboxEventStatus.PENDING)
                .createdAt(NOW)
                .build();
    }

    private static StoredFile storedFile() {
        return StoredFile.builder()
                .id(99L)
                .fileName("Offer_signed.pdf")
                .mimeType("application/pdf")
                .sizeBytes(3)
                .externalFileId("external-id")
                .status(FileStatus.ACTIVE)
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();
    }
}
