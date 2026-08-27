package com.hirewise.be.service;

import com.hirewise.be.logging.LogMaskUtils;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * {@link EmailService} implementation backed by {@link JavaMailSender}
 * (SMTP settings from {@code spring.mail.*} / .env - see application.properties).
 * <p>
 * Templates are kept as simple inline HTML rather than pulling in a
 * template engine (Thymeleaf/Freemarker) - the email bodies here are short
 * and few in number; a template engine would be worth adding once
 * EMAIL_TEMPLATE_MANAGE (see {@code authorization.PermissionCodes}) is
 * actually implemented as a user-editable feature.
 */
@Slf4j
@Service
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String productName;

    public EmailServiceImpl(JavaMailSender mailSender,
                             @Value("${app.mail.from:no-reply@hirewise.local}") String fromAddress,
                             @Value("${app.mail.product-name:HireWise}") String productName) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.productName = productName;
    }

    @Override
    public void sendActivationEmail(String toEmail, String fullName, String activationLink) {
        String subject = productName + " - Kich hoat tai khoan cua ban";
        String greeting = fullName == null || fullName.isBlank() ? "Xin chao" : "Xin chao " + fullName;
        String plainText = "%s,\n\nTai khoan noi bo cua ban tren %s da duoc tao. Vui long truy cap lien ket ben duoi de dat mat khau va kich hoat tai khoan:\n%s\n\nLien ket nay se het han sau mot khoang thoi gian gioi han. Neu ban khong yeu cau, vui long bo qua email nay."
                .formatted(greeting, productName, activationLink);
        String html = """
                <!DOCTYPE html>
                <html>
                <body>
                    <p>%s,</p>
                    <p>Tai khoan noi bo cua ban tren %s da duoc tao. Vui long bam vao lien ket ben duoi de dat mat khau va kich hoat tai khoan:</p>
                    <p><a href="%s" target="_blank" style="display:inline-block;padding:10px 20px;background-color:#2563eb;color:#ffffff;text-decoration:none;border-radius:6px;">Kich hoat tai khoan</a></p>
                    <p style="color:#6b7280;font-size:12px;">Hoac mo duong link: <a href="%s">%s</a></p>
                    <p>Lien ket nay se het han sau mot khoang thoi gian gioi han. Neu ban khong yeu cau, vui long bo qua email nay.</p>
                </body>
                </html>
                """.formatted(greeting, productName, activationLink, activationLink, activationLink);
        send(toEmail, subject, plainText, html);
    }

    @Override
    public void sendSecurityAlertEmail(String toEmail, String fullName, String ipAddress) {
        String subject = productName + " - Canh bao dang nhap that bai nhieu lan";
        String greeting = fullName == null || fullName.isBlank() ? "Xin chao" : "Xin chao " + fullName;
        String plainText = "%s,\n\nHe thong ghi nhan 5 lan dang nhap sai lien tiep vao tai khoan cua ban tu dia chi IP %s. Tai khoan cua ban da bi tam khoa trong 15 phut de bao ve an toan.\n\nNeu day khong phai la ban, vui long doi mat khau ngay sau khi tai khoan duoc mo lai va lien he quan tri vien."
                .formatted(greeting, ipAddress == null ? "khong xac dinh" : ipAddress);
        String html = """
                <!DOCTYPE html>
                <html>
                <body>
                    <p>%s,</p>
                    <p>He thong ghi nhan 5 lan dang nhap sai lien tiep vao tai khoan cua ban tu dia chi IP %s. Tai khoan cua ban da bi tam khoa trong 15 phut de bao ve an toan.</p>
                    <p>Neu day khong phai la ban, vui long doi mat khau ngay sau khi tai khoan duoc mo lai va lien he quan tri vien.</p>
                </body>
                </html>
                """.formatted(greeting, ipAddress == null ? "khong xac dinh" : ipAddress);
        send(toEmail, subject, plainText, html);
    }

    @Override
    public void sendApplicationConfirmationEmail(String toEmail, String fullName, String jobTitle) {
        String subject = productName + " - Xac nhan da nhan ho so ung tuyen";
        String greeting = fullName == null || fullName.isBlank() ? "Xin chao" : "Xin chao " + fullName;
        
        String plainText = "%s,\n\n%s da nhan duoc ho so ung tuyen cua ban cho vi tri %s.\n\nDoi ngu tuyen dung se xem xet ho so va lien he lai voi ban qua email/so dien thoai da cung cap neu ho so phu hop.\n\nCam on ban da quan tam den co hoi nghe nghiep tai %s."
                .formatted(greeting, productName, jobTitle, productName);
                
        String html = """
                <!DOCTYPE html>
                <html>
                <body>
                    <p>%s,</p>
                    <p>%s da nhan duoc ho so ung tuyen cua ban cho vi tri <strong>%s</strong>.</p>
                    <p>Doi ngu tuyen dung se xem xet ho so va lien he lai voi ban qua email/so dien thoai da cung cap neu ho so phu hop.</p>
                    <p>Cam on ban da quan tam den co hoi nghe nghiep tai %s.</p>
                </body>
                </html>
                """.formatted(greeting, productName, jobTitle, productName);
                
        send(toEmail, subject, plainText, html);
    }

    @Override
    public void sendJobApprovalDecisionEmail(String toEmail, String recruiterName,
                                              String jobTitle, boolean approved, String reason) {
        String greeting = recruiterName == null || recruiterName.isBlank()
                ? "Xin chao" : "Xin chao " + recruiterName;
        String decisionVi = approved ? "DA DUOC PHE DUYET" : "DA BI TU CHOI";
        String subject = productName + " - Vi tri \"" + jobTitle + "\" " + decisionVi;

        String plainText;
        String html;
        if (approved) {
            plainText = "%s,\n\nVi tri tuyen dung \"%s\" cua ban da duoc Hiring Manager phe duyet thanh cong.\n\nHay tien hanh cong bo vi tri nay len trang tuyen dung cong khai (UC-16).\n\nTran trong,\n%s"
                    .formatted(greeting, jobTitle, productName);
            html = """
                    <!DOCTYPE html>
                    <html>
                    <body>
                        <p>%s,</p>
                        <p>Vi tri tuyen dung <strong>%s</strong> cua ban da duoc Hiring Manager <span style="color:#16a34a;font-weight:bold;">phe duyet</span> thanh cong.</p>
                        <p>Hay tien hanh cong bo vi tri nay len trang tuyen dung cong khai.</p>
                        <p style="color:#6b7280;font-size:12px;">%s</p>
                    </body>
                    </html>
                    """.formatted(greeting, jobTitle, productName);
        } else {
            String reasonText = reason == null || reason.isBlank() ? "(Khong co ly do)" : reason;
            plainText = "%s,\n\nRat tiec, vi tri tuyen dung \"%s\" cua ban da bi Hiring Manager tu choi.\n\nLy do: %s\n\nVui long chinh sua va gui lai de xem xet.\n\nTran trong,\n%s"
                    .formatted(greeting, jobTitle, reasonText, productName);
            html = """
                    <!DOCTYPE html>
                    <html>
                    <body>
                        <p>%s,</p>
                        <p>Rat tiec, vi tri tuyen dung <strong>%s</strong> cua ban da bi Hiring Manager <span style="color:#dc2626;font-weight:bold;">tu choi</span>.</p>
                        <p><strong>Ly do:</strong> %s</p>
                        <p>Vui long chinh sua theo phan hoi va gui lai de duoc xem xet.</p>
                        <p style="color:#6b7280;font-size:12px;">%s</p>
                    </body>
                    </html>
                    """.formatted(greeting, jobTitle, reasonText, productName);
        }
        send(toEmail, subject, plainText, html);
    }

    @Override
    public void sendJobSubmittedForApprovalEmail(String toEmail, String hiringManagerName,
                                                  String jobTitle, String recruiterName) {
        String greeting = hiringManagerName == null || hiringManagerName.isBlank()
                ? "Xin chao" : "Xin chao " + hiringManagerName;
        String recruiterText = recruiterName == null || recruiterName.isBlank()
                ? "Mot Recruiter" : recruiterName;
        String subject = productName + " - Vi tri \"" + jobTitle + "\" can duoc phe duyet";
        String plainText = "%s,\n\n%s da gui yeu cau tuyen dung \"%s\" de cho ban xem xet phe duyet.\n\nVui long dang nhap he thong de xem chi tiet va Phe duyet/Tu choi.\n\nTran trong,\n%s"
                .formatted(greeting, recruiterText, jobTitle, productName);
        String html = """
                <!DOCTYPE html>
                <html>
                <body>
                    <p>%s,</p>
                    <p><strong>%s</strong> da gui yeu cau tuyen dung <strong>%s</strong> de cho ban xem xet phe duyet.</p>
                    <p>Vui long dang nhap he thong de xem chi tiet va Phe duyet/Tu choi.</p>
                    <p style="color:#6b7280;font-size:12px;">%s</p>
                </body>
                </html>
                """.formatted(greeting, recruiterText, jobTitle, productName);
        send(toEmail, subject, plainText, html);
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

    /** Wraps any checked/unchecked failure from the underlying mail transport. */
    public static class EmailDeliveryException extends RuntimeException {
        public EmailDeliveryException(Throwable cause) {
            super(cause);
        }
    }
}
