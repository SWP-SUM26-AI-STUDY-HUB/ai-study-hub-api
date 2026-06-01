package vn.ai_study_hub_api.controller.request;
import lombok.Data;
@Data
public class RegisterRequest {
    private String email;
    private String password;
    private String fullName;
}