package com.hirewise.be.service;

import com.hirewise.be.domain.Offer;
import com.hirewise.be.domain.OfferTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * UC-36 step 5: substitutes the {@code {{Placeholder}}} variables of an
 * {@link OfferTemplate} body with a concrete offer's data, producing the
 * {@code offers.rendered_body} snapshot the candidate later reads (UC-38)
 * and signs (UC-39).
 * <p>
 * Uses the same placeholder syntax as {@code email_templates} so HR only
 * ever learns one convention. Every substituted value is HTML-escaped
 * first: template bodies are rendered straight into the candidate's browser,
 * and a candidate-controlled field (their own name) reaching the page
 * unescaped would be stored XSS.
 */
@Component
public class OfferTemplateRenderer {

    private static final DateTimeFormatter VI_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final String productName;

    public OfferTemplateRenderer(@Value("${app.mail.product-name:HireWise}") String productName) {
        this.productName = productName;
    }

    /**
     * Renders the offer letter body for one Offer.
     *
     * @param template      the template whose {@code bodyTemplate} is rendered
     * @param offer         the offer supplying salary/dates; its Application,
     *                       Candidate and Job associations must be reachable
     * @param recruiterName full name of the Recruiter creating the offer
     * @return the rendered body, safe to embed in an HTML page
     */
    public String render(OfferTemplate template, Offer offer, String recruiterName) {
        Map<String, String> variables = new HashMap<>();
        variables.put("Candidate_Name", offer.getApplication().getCandidate().getFullName());
        variables.put("Job_Title", offer.getApplication().getJobPosition().getTitle());
        variables.put("Company", productName);
        variables.put("Recruiter_Name", recruiterName);
        variables.put("Salary", formatMoney(offer.getSalary()));
        variables.put("Probation_Rate", formatPercent(offer.getProbationRate()));
        variables.put("Start_Date", formatDate(offer.getStartDate()));
        variables.put("Expiry_Date", VI_DATE_FORMATTER.format(offer.getExpiresAt()));

        String body = template.getBodyTemplate();
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            body = body.replace("{{" + entry.getKey() + "}}", escapeHtml(entry.getValue()));
        }
        return body;
    }

    /** "25000000" -> "25.000.000 VND", the format Vietnamese offer letters use. */
    private static String formatMoney(BigDecimal salary) {
        return String.format(Locale.GERMANY, "%,.0f VND", salary);
    }

    /** A null probation rate means the Recruiter left the optional field blank. */
    private static String formatPercent(BigDecimal probationRate) {
        return probationRate == null ? "-" : probationRate.stripTrailingZeros().toPlainString() + "%";
    }

    private static String formatDate(LocalDate date) {
        return date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    /**
     * Minimal HTML escaping. Deliberately hand-rolled rather than pulling in
     * a new dependency - only the five characters that can break out of text
     * or attribute context matter here.
     */
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
