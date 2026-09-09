package com.hirewise.be.service;

import com.hirewise.be.domain.Offer;
import com.hirewise.be.domain.SignatureMethod;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.util.XRLog;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    static {
        // openhtmltopdf logs a dozen INFO lines through java.util.logging on
        // every single render, bypassing the app's Logback config entirely.
        // Signing an offer is a business event, not a rendering demo - the
        // one line OfferSigningService already logs is the useful record.
        XRLog.setLoggingEnabled(false);
    }

    private static final DateTimeFormatter VI_DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    /** Bundled font covering Vietnamese; see {@link #registerFonts}. */
    private static final String FONT_FAMILY = "DejaVu Sans";

    private static final String FONT_RESOURCE_DIR = "/fonts/";

    /** Matches a named entity reference, e.g. {@code &middot;} - not a numeric one. */
    private static final Pattern NAMED_ENTITY = Pattern.compile("&([a-zA-Z][a-zA-Z0-9]{0,31});");

    /** The only entity names an XML parser knows without a DTD. */
    private static final Set<String> XML_PREDEFINED = Set.of("amp", "lt", "gt", "quot", "apos");

    /**
     * Named HTML entities mapped to their code points. Covers what a rich-text
     * editor realistically emits into a Vietnamese offer letter; anything
     * missing degrades to literal text rather than breaking the render.
     */
    private static final Map<String, Integer> HTML_ENTITY_CODE_POINTS = Map.ofEntries(
            Map.entry("nbsp", 160), Map.entry("middot", 183), Map.entry("bull", 8226),
            Map.entry("ndash", 8211), Map.entry("mdash", 8212), Map.entry("hellip", 8230),
            Map.entry("ldquo", 8220), Map.entry("rdquo", 8221), Map.entry("lsquo", 8216),
            Map.entry("rsquo", 8217), Map.entry("laquo", 171), Map.entry("raquo", 187),
            Map.entry("deg", 176), Map.entry("copy", 169), Map.entry("reg", 174),
            Map.entry("trade", 8482), Map.entry("euro", 8364), Map.entry("pound", 163),
            Map.entry("yen", 165), Map.entry("cent", 162), Map.entry("sect", 167),
            Map.entry("para", 182), Map.entry("dagger", 8224), Map.entry("permil", 8240),
            Map.entry("times", 215), Map.entry("divide", 247), Map.entry("plusmn", 177),
            Map.entry("frac12", 189), Map.entry("frac14", 188), Map.entry("frac34", 190));

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
            registerFonts(builder);
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

    /**
     * Embeds the bundled DejaVu Sans faces.
     * <p>
     * Without this the renderer falls back to PDFBox's built-in Helvetica, a
     * base-14 font limited to WinAnsi - which has no Vietnamese diacritics, so
     * every "ế", "ữ" or "ạ" came out broken. That hit real data hardest: an
     * offer letter is addressed to a candidate whose name almost always
     * carries diacritics.
     * <p>
     * The faces are bundled rather than taken from the host so a Linux
     * container renders identically to a developer's Windows machine.
     * Subsetting is on, so only the glyphs actually used are written into each
     * PDF and the output stays small despite the fonts being ~1.7 MB on disk.
     */
    private static void registerFonts(PdfRendererBuilder builder) {
        builder.useFont(() -> fontStream("DejaVuSans.ttf"), FONT_FAMILY, 400, FontStyle.NORMAL, true);
        builder.useFont(() -> fontStream("DejaVuSans-Bold.ttf"), FONT_FAMILY, 700, FontStyle.NORMAL, true);
        // The typed signature is rendered in italic; with no italic face
        // registered the renderer would drop back to Helvetica for exactly the
        // one line that is a person's name.
        builder.useFont(() -> fontStream("DejaVuSans-Oblique.ttf"), FONT_FAMILY, 400, FontStyle.ITALIC, true);
    }

    private static InputStream fontStream(String fileName) {
        InputStream stream = OfferPdfRenderer.class.getResourceAsStream(FONT_RESOURCE_DIR + fileName);
        if (stream == null) {
            // Fail loudly at render time rather than silently producing a
            // contract with unreadable Vietnamese in it.
            throw new IllegalStateException("Missing bundled PDF font: " + FONT_RESOURCE_DIR + fileName);
        }
        return stream;
    }

    private String buildHtml(Offer offer, SignatureMethod method, String signerName,
                              String signatureImageDataUri, Instant signedAt) {
        String signatureMark = method == SignatureMethod.DRAW && signatureImageDataUri != null
                ? "<img src=\"" + signatureImageDataUri + "\" style=\"max-height:80px;\" alt=\"\" />"
                // Must name the embedded family: "serif" would resolve to a
                // base-14 font and mangle the diacritics in the signer's name.
                : "<span style=\"font-family:'" + FONT_FAMILY + "';font-style:italic;font-size:22px;\">"
                        + escapeHtml(signerName) + "</span>";

        // openhtmltopdf needs well-formed XHTML; renderedBody is produced by
        // OfferTemplateRenderer from an HR-authored template, and every value
        // substituted into it was escaped there - but a named HTML entity in
        // that template would still abort the parse, so normalise it first.
        return """
                <html><head><meta charset="UTF-8" /><style>
                  @page { size: A4; margin: 20mm; }
                  body { font-family: "DejaVu Sans", sans-serif; font-size: 12px; line-height: 1.5; }
                  .signature-block { margin-top: 40px; border-top: 1px solid #999; padding-top: 16px; }
                  .signature-meta { font-size: 10px; color: #555; margin-top: 8px; }
                </style></head><body>
                """
                + toXmlSafeEntities(offer.getRenderedBody())
                + "<div class=\"signature-block\">"
                + "<p><strong>Chu ky dien tu cua ung vien</strong></p>"
                + "<p>" + signatureMark + "</p>"
                + "<p>" + escapeHtml(signerName) + "</p>"
                + "<p class=\"signature-meta\">Hinh thuc ky: " + method.name()
                + " &#183; Thoi diem ky: " + VI_DATE_TIME_FORMATTER.format(signedAt)
                + " &#183; Ma Offer: " + offer.getId()
                + "</p></div></body></html>";
    }

    /**
     * Rewrites named HTML entities into numeric character references, which
     * are the only form an XML parser accepts.
     * <p>
     * openhtmltopdf parses the document as strict XML, where the only
     * predefined names are {@code amp lt gt quot apos} - everything else
     * ({@code &middot;}, {@code &nbsp;}, ...) aborts the parse with
     * "entity was referenced, but not declared". That would surface at the
     * worst possible moment: the candidate pressing [Hoan tat ky] on a job
     * they have decided to accept. Offer bodies come from
     * {@code offer_templates.body_template}, authored by HR in an editor
     * that emits such entities freely, so they must be normalised rather
     * than merely avoided in our own markup.
     * <p>
     * A name that is not recognised has its ampersand escaped instead, so an
     * unknown entity renders as literal text rather than failing the render.
     */
    static String toXmlSafeEntities(String html) {
        if (html == null) {
            return "";
        }
        Matcher matcher = NAMED_ENTITY.matcher(html);
        StringBuilder result = new StringBuilder(html.length());
        while (matcher.find()) {
            String name = matcher.group(1);
            String replacement;
            if (XML_PREDEFINED.contains(name)) {
                replacement = matcher.group();
            } else {
                Integer codePoint = HTML_ENTITY_CODE_POINTS.get(name);
                replacement = codePoint != null ? "&#" + codePoint + ";" : "&amp;" + name + ";";
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
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
