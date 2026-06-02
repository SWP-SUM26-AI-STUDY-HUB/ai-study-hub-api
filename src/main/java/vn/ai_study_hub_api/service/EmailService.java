package vn.ai_study_hub_api.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

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
        // Link này sau này ông đổi thành đường dẫn giao diện Reset Mật khẩu của Frontend nhé
        String resetLink = "http://localhost:3000/reset-password?token=" + resetToken;

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
}