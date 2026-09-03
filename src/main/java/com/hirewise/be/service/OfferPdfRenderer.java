package com.hirewise.be.service;

import com.hirewise.be.domain.Offer;
import com.hirewise.be.domain.SignatureMethod;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * UC-39 step 4: renders the signed Offer letter to a PDF (BR-OFFER-04).
 * <p>
 * The document is built from the Offer's own {@code renderedBody} - the
 * snapshot frozen at creation (UC-36 step 5) - with a signature block
 * appended, so the archived artifact is provably the same wording the
 * candidate read on screen rather than a second, independently laid-out
 * rendering that could drift from it.
 */
@Component
public class OfferPdfRenderer {

    private static final DateTimeFormatter VI_DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    /**
     * Builds the signed PDF.
     *
     * @param offer                 the offer being signed; its rendered body becomes the document
     * @param method                how the candidate signed (LV-22)
     * @param signerName            the name typed, or the name shown under a drawn signature
     * @param signatureImageDataUri a {@code data:image/png;base64,...} URI of the drawn
     *                              signature; {@code null} for {@link SignatureMethod#TYPE}
     * @param signedAt              the signing instant stamped onto the document
     * @return the PDF bytes
     */
    public byte[] render(Offer offer, SignatureMethod method, String signerName,
                          String signatureImageDataUri, Instant signedAt) {
        String html = buildHtml(offer, method, signerName, signatureImageDataUri, signedAt);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            // No base URI is given on purpose: the document must not be able to
            // pull in anything off the filesystem or the network while rendering.
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to render the signed Offer PDF", e);
        }
    }

    private String buildHtml(Offer offer, SignatureMethod method, String signerName,
                              String signatureImageDataUri, Instant signedAt) {
        String signatureMark = method == SignatureMethod.DRAW && signatureImageDataUri != null
                ? "<img src=\"" + signatureImageDataUri + "\" style=\"max-height:80px;\" alt=\"\" />"
                : "<span style=\"font-family:serif;font-style:italic;font-size:22px;\">"
                        + escapeHtml(signerName) + "</span>";

        // openhtmltopdf needs well-formed XHTML; renderedBody is produced by
        // OfferTemplateRenderer from an HR-authored template, and every value
        // substituted into it was escaped there.
        return """
                <html><head><meta charset="UTF-8" /><style>
                  @page { size: A4; margin: 20mm; }
                  body { font-family: sans-serif; font-size: 12px; line-height: 1.5; }
                  .signature-block { margin-top: 40px; border-top: 1px solid #999; padding-top: 16px; }
                  .signature-meta { font-size: 10px; color: #555; margin-top: 8px; }
                </style></head><body>
                """
                + offer.getRenderedBody()
                + "<div class=\"signature-block\">"
                + "<p><strong>Chu ky dien tu cua ung vien</strong></p>"
                + "<p>" + signatureMark + "</p>"
                + "<p>" + escapeHtml(signerName) + "</p>"
                + "<p class=\"signature-meta\">Hinh thuc ky: " + method.name()
                + " &middot; Thoi diem ky: " + VI_DATE_TIME_FORMATTER.format(signedAt)
                + " &middot; Ma Offer: " + offer.getId()
                + "</p></div></body></html>";
    }

    /** Same minimal escaping as {@link OfferTemplateRenderer} - see the note there. */
    private static String escapeHtml(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
