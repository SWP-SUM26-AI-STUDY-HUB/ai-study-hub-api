package vn.ai_study_hub_api.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import vn.ai_study_hub_api.model.UserEntity;
import vn.ai_study_hub_api.repository.UserRepository;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        // 1. Lấy email từ đối tượng Google trả về
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = oAuth2User.getAttribute("email");

        // 2. Lấy thông tin user từ DB để tạo đối tượng CustomUserDetails cho JwtTokenProvider
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // 3. Chuyển đổi sang CustomUserDetails (Bạn cần class này để dùng generateAccessToken)
        CustomUserDetails userPrincipal = CustomUserDetails.build(user);

        // 4. Dùng JwtTokenProvider tạo token
        String accessToken = tokenProvider.generateAccessToken(userPrincipal);

        // 5. Redirect về Frontend kèm token trên URL
        String targetUrl = "http://localhost:4300/auth/callback?token=" + accessToken;
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}