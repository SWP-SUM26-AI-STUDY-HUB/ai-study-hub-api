package vn.ai_study_hub_api.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Autowired
    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendOtpEmail(String to, String otp) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("Your OTP Verification Code");
        message.setText("Your OTP code is: " + otp + ". This code is valid for 5 minutes.");
        mailSender.send(message);
    }
    public void sendResetPasswordEmail(String toEmail, String resetToken) {
        String baseUrl = frontendUrl;
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        String encodedEmail = java.net.URLEncoder.encode(toEmail, java.nio.charset.StandardCharsets.UTF_8);
        String resetLink = baseUrl + "/reset-password?email=" + encodedEmail + "&token=" + resetToken;

        jakarta.mail.internet.MimeMessage message = mailSender.createMimeMessage();
        try {
            org.springframework.mail.javamail.MimeMessageHelper helper =
                    new org.springframework.mail.javamail.MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(toEmail);
            helper.setSubject("[AI Study Hub] - Reset Your Password");

            // Nội dung email dạng HTML xịn sò có nút bấm
            String htmlContent = "<h3>Hello,</h3>"
                    + "<p>You requested to reset your password. Please click the link below to set a new password:</p>"
                    + "<p><a href=\"" + resetLink + "\" style=\"background-color: #4CAF50; color: white; padding: 10px 20px; text-decoration: none; display: inline-block; border-radius: 5px;\">Reset Password</a></p>"
                    + "<p>This link will expire in <b>15 minutes</b>.</p>"
                    + "<p>If you did not make this request, please ignore this email.</p>"
                    + "<br><p>Best regards,<br><b>AI Study Hub Team</b></p>";

            helper.setText(htmlContent, true);
            mailSender.send(message);

        } catch (jakarta.mail.MessagingException e) {
            throw new RuntimeException("Failed to send email: " + e.getMessage());
        }
    }

    public void sendBanEmail(String toEmail, String reason) {
        jakarta.mail.internet.MimeMessage message = mailSender.createMimeMessage();
        try {
            org.springframework.mail.javamail.MimeMessageHelper helper =
                    new org.springframework.mail.javamail.MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(toEmail);
            helper.setSubject("[AI Study Hub] - Tài khoản của bạn đã bị khóa");

            String htmlContent = "<h3>Xin chào,</h3>"
                    + "<p>Chúng tôi rất tiếc phải thông báo rằng tài khoản của bạn tại <b>AI Study Hub</b> đã bị khóa (banned).</p>"
                    + "<p><b>Lý do khóa tài khoản:</b> " + reason + "</p>"
                    + "<p>Nếu bạn cho rằng đây là sự nhầm lẫn hoặc muốn thực hiện khiếu nại, vui lòng liên hệ với bộ phận hỗ trợ của chúng tôi.</p>"
                    + "<br><p>Trân trọng,<br><b>AI Study Hub Team</b></p>";

            helper.setText(htmlContent, true);
            mailSender.send(message);
        } catch (Exception e) {
            // Log warning but do not throw to avoid blocking the ban transaction
            org.slf4j.LoggerFactory.getLogger(EmailService.class)
                .warn("Failed to send ban email to {}: {}", toEmail, e.getMessage());
        }
    }
}