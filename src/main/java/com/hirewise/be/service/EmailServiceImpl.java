package com.hirewise.be.service;

import com.hirewise.be.domain.EmailTemplate;
import com.hirewise.be.domain.EmailTemplateStatus;
import com.hirewise.be.exception.ErrorCode;
import com.hirewise.be.exception.ResourceNotFoundException;
import com.hirewise.be.logging.LogMaskUtils;
import com.hirewise.be.repository.EmailTemplateRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * {@link EmailService} implementation backed by {@link JavaMailSender}
 * and dynamic {@link EmailTemplate} records queried from the database.
 * <p>
 * Email contents and subjects are loaded exclusively from {@code email_templates} table
 * (seeded EM-01..EM-13, EM-SEC, etc.) and populated with runtime parameters.
 */
@Slf4j
@Service
public class EmailServiceImpl implements EmailService {

    private static final DateTimeFormatter VI_DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final JavaMailSender mailSender;
    private final EmailTemplateRepository emailTemplateRepository;
    private final String fromAddress;
    private final String productName;

    public EmailServiceImpl(JavaMailSender mailSender,
                            EmailTemplateRepository emailTemplateRepository,
                            @Value("${app.mail.from:no-reply@hirewise.local}") String fromAddress,
                            @Value("${app.mail.product-name:HireWise}") String productName) {
        this.mailSender = mailSender;
        this.emailTemplateRepository = emailTemplateRepository;
        this.fromAddress = fromAddress;
        this.productName = productName;
    }

    @Override
    public void sendActivationEmail(String toEmail, String fullName, String activationLink) {
        String greetingName = (fullName == null || fullName.isBlank()) ? "ban" : fullName;

        Map<String, String> variables = new HashMap<>();
        variables.put("Full_Name", greetingName);
        variables.put("Candidate_Name", greetingName);
        variables.put("Activation_Link", activationLink != null ? activationLink : "");
        variables.put("Company", productName);
        variables.put("Role_Name", "Thanh vien");
        variables.put("Department_Name", productName);

        sendTemplateEmail(toEmail, "EM-01", variables);
    }

    @Override
    public void sendSecurityAlertEmail(String toEmail, String fullName, String ipAddress) {
        String greetingName = (fullName == null || fullName.isBlank()) ? "ban" : fullName;
        String ipDisplay = (ipAddress == null || ipAddress.isBlank()) ? "khong xac dinh" : ipAddress;

        Map<String, String> variables = new HashMap<>();
        variables.put("Full_Name", greetingName);
        variables.put("IP_Address", ipDisplay);
        variables.put("Company", productName);

        sendTemplateEmail(toEmail, "EM-SEC", variables);
    }

    @Override
    public void sendApplicationConfirmationEmail(String toEmail, String fullName, String jobTitle) {
        String candidateName = (fullName == null || fullName.isBlank()) ? "Ung vien" : fullName;
        String appliedAt = VI_DATE_TIME_FORMATTER.format(Instant.now());

        Map<String, String> variables = new HashMap<>();
        variables.put("Candidate_Name", candidateName);
        variables.put("Full_Name", candidateName);
        variables.put("Job_Title", jobTitle != null ? jobTitle : "");
        variables.put("Company", productName);
        variables.put("Applied_At", appliedAt);

        sendTemplateEmail(toEmail, "EM-04", variables);
    }

    @Override
    public void sendJobApprovalDecisionEmail(String toEmail, String recruiterName,
                                              String jobTitle, boolean approved, String reason) {
        String recName = (recruiterName == null || recruiterName.isBlank()) ? "Recruiter" : recruiterName;
        String decisionVi = approved ? "DA DUOC PHE DUYET" : "DA BI TU CHOI";
        String rejectReasonBlock = (!approved && reason != null && !reason.isBlank())
                ? "Ly do tu choi: " + reason
                : "";

        Map<String, String> variables = new HashMap<>();
        variables.put("Recruiter_Name", recName);
        variables.put("Full_Name", recName);
        variables.put("Job_Title", jobTitle != null ? jobTitle : "");
        variables.put("Manager_Name", "Hiring Manager");
        variables.put("Decision", decisionVi);
        variables.put("Reject_Reason_Block", rejectReasonBlock);
        variables.put("Job_Link", "");
        variables.put("Company", productName);

        sendTemplateEmail(toEmail, "EM-03", variables);
    }

    @Override
    public void sendTemplateEmail(String toEmail, String templateCode, Map<String, String> variables) {
        EmailTemplate template = emailTemplateRepository
                .findByCodeAndStatus(templateCode, EmailTemplateStatus.ACTIVE)
                .or(() -> emailTemplateRepository.findByCode(templateCode))
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.EMAIL_TEMPLATE_NOT_FOUND, templateCode));

        String subject = renderTemplate(template.getSubjectTemplate(), variables);
        String html = renderTemplate(template.getBodyTemplate(), variables);
        String plainText = htmlToPlainText(html);

        send(toEmail, subject, plainText, html);
    }

    private String renderTemplate(String template, Map<String, String> variables) {
        if (template == null) {
            return "";
        }
        String result = template;
        if (variables != null) {
            for (Map.Entry<String, String> entry : variables.entrySet()) {
                if (entry.getKey() != null) {
                    String placeholder = "{{" + entry.getKey() + "}}";
                    String val = entry.getValue() != null ? entry.getValue() : "";
                    result = result.replace(placeholder, val);
                }
            }
        }
        // Remove any remaining unresolved {{...}} placeholders
        return result.replaceAll("\\{\\{[^}]+}}", "");
    }

    private String htmlToPlainText(String html) {
        if (html == null) {
            return "";
        }
        return html
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</p>", "\n\n")
                .replaceAll("(?i)</li>", "\n")
                .replaceAll("(?i)</tr>", "\n")
                .replaceAll("(?i)</div>", "\n")
                .replaceAll("<[^>]+>", "")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replaceAll("\n{3,}", "\n\n")
                .trim();
    }

    private void send(String toEmail, String subject, String plainText, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(plainText, html);
            mailSender.send(message);
            log.info("Sent email to {}: {}", LogMaskUtils.maskEmail(toEmail), subject);
        } catch (Exception e) {
            // Re-thrown as unchecked so OutboxDispatcher can catch it, mark the outbox
            // row FAILED and retry later - never let an email failure surface to the
            // original HTTP request (the row was already committed independently).
            throw new EmailDeliveryException(e);
        }
    }

    @Override
    public void sendJobSubmittedForApprovalEmail(String toEmail, String hiringManagerName,
                                                  String jobTitle, String recruiterName) {
        String managerName = (hiringManagerName == null || hiringManagerName.isBlank()) ? "Hiring Manager" : hiringManagerName;
        String recName = (recruiterName == null || recruiterName.isBlank()) ? "Mot Recruiter" : recruiterName;

        Map<String, String> variables = new HashMap<>();
        variables.put("Manager_Name", managerName);
        variables.put("Recruiter_Name", recName);
        variables.put("Job_Title", jobTitle != null ? jobTitle : "");
        variables.put("Department_Name", "phong ban cua ban");
        variables.put("Openings", "mot so");
        variables.put("Job_Approval_Link", "");
        variables.put("Company", productName);

        sendTemplateEmail(toEmail, "EM-02", variables);
    }

    @Override
    public void sendApplicationRejectionEmail(String toEmail, String candidateName,
                                               String jobTitle, String reasonLabel, String customMessage) {
        String candName = (candidateName == null || candidateName.isBlank()) ? "Ung vien" : candidateName;
        String reason = (reasonLabel == null || reasonLabel.isBlank()) ? "" : reasonLabel;
        String note = (customMessage == null || customMessage.isBlank()) ? "" : " (" + customMessage + ")";
        String customMessageBlock = reason.isBlank() ? "." : " vi ly do: " + reason + note + ".";

        Map<String, String> variables = new HashMap<>();
        variables.put("Candidate_Name", candName);
        variables.put("Job_Title", jobTitle != null ? jobTitle : "");
        variables.put("Company", productName);
        variables.put("Custom_Message_Block", customMessageBlock);

        sendTemplateEmail(toEmail, "EM-09", variables);
    }

    @Override
    public void sendOfferEmail(String toEmail, String candidateName, String jobTitle,
                                String offerLink, String expiryDate, String recruiterName) {
        String candName = (candidateName == null || candidateName.isBlank()) ? "Ung vien" : candidateName;
        String recName = (recruiterName == null || recruiterName.isBlank()) ? "Recruiter" : recruiterName;

        Map<String, String> variables = new HashMap<>();
        variables.put("Candidate_Name", candName);
        variables.put("Full_Name", candName);
        variables.put("Job_Title", jobTitle != null ? jobTitle : "");
        variables.put("Company", productName);
        variables.put("Offer_Link", offerLink != null ? offerLink : "");
        variables.put("Expiry_Date", expiryDate != null ? expiryDate : "");
        variables.put("Recruiter_Name", recName);

        sendTemplateEmail(toEmail, "EM-11", variables);
    }

    /** Wraps any checked/unchecked failure from the underlying mail transport. */
    public static class EmailDeliveryException extends RuntimeException {
        public EmailDeliveryException(Throwable cause) {
            super(cause);
        }
    }
}
