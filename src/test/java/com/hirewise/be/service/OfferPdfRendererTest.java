package com.hirewise.be.service;

import com.hirewise.be.domain.Application;
import com.hirewise.be.domain.Candidate;
import com.hirewise.be.domain.JobPosition;
import com.hirewise.be.domain.Offer;
import com.hirewise.be.domain.OfferStatus;
import com.hirewise.be.domain.SignatureMethod;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * UC-39 step 4: actually renders the PDF (BR-OFFER-04).
 * <p>
 * {@code OfferSigningServiceTest} mocks this renderer, so nothing there
 * exercises the real openhtmltopdf parse. These tests do - which is the
 * only way a malformed-markup failure gets caught before a candidate hits
 * it while accepting a job.
 */
class OfferPdfRendererTest {

    private static final Instant SIGNED_AT = Instant.parse("2026-09-04T03:00:00Z");
    private static final byte[] PDF_MAGIC = "%PDF".getBytes(StandardCharsets.US_ASCII);

    private final OfferPdfRenderer renderer = new OfferPdfRenderer();

    @Test
    void rendersATypedSignatureToAPdf() {
        byte[] pdf = renderer.render(offer("<p>Dieu khoan</p>"), SignatureMethod.TYPE,
                "Nguyen Van A", null, SIGNED_AT);

        assertThat(pdf).isNotEmpty().startsWith(PDF_MAGIC);
    }

    @Test
    void rendersADrawnSignatureToAPdf() {
        // 1x1 transparent PNG - enough for the renderer to embed an image.
        String png = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJ"
                + "AAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";

        byte[] pdf = renderer.render(offer("<p>Dieu khoan</p>"), SignatureMethod.DRAW,
                "Nguyen Van A", png, SIGNED_AT);

        assertThat(pdf).isNotEmpty().startsWith(PDF_MAGIC);
    }

    /**
     * The signature block stamps a separator between its metadata fields.
     * Written as a named HTML entity it aborted the XML parse and took the
     * whole signing transaction down with it.
     */
    @Test
    void signatureBlockSeparatorDoesNotBreakTheXmlParse() {
        assertThatCode(() -> renderer.render(offer("<p>Dieu khoan</p>"), SignatureMethod.TYPE,
                "Nguyen Van A", null, SIGNED_AT))
                .doesNotThrowAnyException();
    }

    @Test
    void namedEntitiesInAnHrAuthoredBodyStillRender() {
        String body = "<p>Luong&nbsp;25.000.000&nbsp;VND &middot; thu viec 85&#37;</p>"
                + "<p>Thoi han: 01/10/2026 &ndash; 31/12/2026 &hellip;</p>"
                + "<p>&copy; HireWise&trade;</p>";

        byte[] pdf = renderer.render(offer(body), SignatureMethod.TYPE, "Nguyen Van A", null, SIGNED_AT);

        assertThat(pdf).isNotEmpty().startsWith(PDF_MAGIC);
    }

    @Test
    void unknownEntityIsKeptAsTextRatherThanFailingTheRender() {
        byte[] pdf = renderer.render(offer("<p>Ma tham chieu &notarealentity; cua ho so</p>"),
                SignatureMethod.TYPE, "Nguyen Van A", null, SIGNED_AT);

        assertThat(pdf).isNotEmpty().startsWith(PDF_MAGIC);
    }

    @Test
    void toXmlSafeEntities_convertsNamedEntitiesToNumericOnes() {
        assertThat(OfferPdfRenderer.toXmlSafeEntities("a &middot; b")).isEqualTo("a &#183; b");
        assertThat(OfferPdfRenderer.toXmlSafeEntities("x&nbsp;y")).isEqualTo("x&#160;y");
    }

    @Test
    void toXmlSafeEntities_leavesTheFiveXmlPredefinedNamesAlone() {
        String xmlSafe = "&amp; &lt; &gt; &quot; &apos;";

        assertThat(OfferPdfRenderer.toXmlSafeEntities(xmlSafe)).isEqualTo(xmlSafe);
    }

    @Test
    void toXmlSafeEntities_escapesTheAmpersandOfAnUnknownName() {
        // Renders as visible text instead of aborting the parse.
        assertThat(OfferPdfRenderer.toXmlSafeEntities("&madeup;")).isEqualTo("&amp;madeup;");
    }

    @Test
    void toXmlSafeEntities_leavesNumericReferencesUntouched() {
        assertThat(OfferPdfRenderer.toXmlSafeEntities("&#183; &#x2014;")).isEqualTo("&#183; &#x2014;");
    }

    private static Offer offer(String renderedBody) {
        Candidate candidate = new Candidate();
        candidate.setId(UUID.randomUUID());
        candidate.setFullName("Nguyen Van A");

        JobPosition job = new JobPosition();
        job.setId(UUID.randomUUID());
        job.setTitle("Backend Engineer");

        Application application = Application.builder()
                .id(UUID.randomUUID())
                .candidate(candidate)
                .jobPosition(job)
                .appliedAt(SIGNED_AT)
                .createdAt(SIGNED_AT)
                .updatedAt(SIGNED_AT)
                .build();

        return Offer.builder()
                .id(UUID.randomUUID())
                .application(application)
                .salary(new BigDecimal("25000000"))
                .probationRate(new BigDecimal("85.00"))
                .startDate(LocalDate.of(2026, 10, 1))
                .expiresAt(SIGNED_AT.plusSeconds(86_400))
                .status(OfferStatus.SENT)
                .renderedBody(renderedBody)
                .createdAt(SIGNED_AT)
                .updatedAt(SIGNED_AT)
                .build();
    }

    /** Guards the assumption that the 1x1 PNG fixture above is valid base64. */
    @Test
    void pngFixtureIsValidBase64() {
        assertThatCode(() -> Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJ"
                        + "AAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="))
                .doesNotThrowAnyException();
    }
}
