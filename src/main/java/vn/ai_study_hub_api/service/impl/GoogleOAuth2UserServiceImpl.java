package vn.ai_study_hub_api.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import vn.ai_study_hub_api.service.UserService;


@Service
@RequiredArgsConstructor
public class GoogleOAuth2UserServiceImpl extends DefaultOAuth2UserService{

    private final UserService userService;
    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        // 1. Gọi về Google để lấy thông tin user
        OAuth2User oAuth2User = super.loadUser(userRequest);

        // 2. Trích xuất thông tin từ thuộc tính của Google
        String email = oAuth2User.getAttribute("email");
        String fullName = oAuth2User.getAttribute("name");
        String avatarUrl = oAuth2User.getAttribute("picture");
        String googleId = oAuth2User.getAttribute("sub");

        // 3. Gọi service đã code để đồng bộ vào DB
        // Đảm bảo truyền đủ 4 tham số như trong Interface/Impl đã sửa
        userService.createOrUpdateUserFromOAuth2(email, fullName, avatarUrl, googleId);

        return oAuth2User;
    }
}
