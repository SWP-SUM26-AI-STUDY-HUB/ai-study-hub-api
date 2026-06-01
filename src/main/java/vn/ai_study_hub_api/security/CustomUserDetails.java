package vn.ai_study_hub_api.security;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import vn.ai_study_hub_api.model.UserEntity;
import vn.ai_study_hub_api.model.UserRole;
import vn.ai_study_hub_api.model.UserStatus;

import java.util.Collection;
import java.util.Collections;
import java.util.UUID;

/**
 * Custom UserDetails implementation holding authenticated UserEntity details.
 * Lớp này giúp Spring Security hiểu được thông tin user của hệ thống chúng ta.
 */
@Getter
@AllArgsConstructor
public class CustomUserDetails implements UserDetails {

    private final UUID id;
    private final String email;
    private final String password;
    private final boolean active;
    private final Collection<? extends GrantedAuthority> authorities;

    /**
     * Map UserEntity vào instance của CustomUserDetails.
     * Sử dụng Enum thay vì String để đảm bảo tính an toàn dữ liệu.
     * * @param user Đối tượng UserEntity từ database
     * @return CustomUserDetails đã được cấu hình
     */
    public static CustomUserDetails build(UserEntity user) {
        // Lấy vai trò (Role) từ Enum và gán tiền tố ROLE_ cho Spring Security
        // .name() trả về giá trị kiểu String của Enum
        String roleStr = user.getRole().name();
        GrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + roleStr.toUpperCase());

        // Kiểm tra trạng thái (Status) bằng cách so sánh trực tiếp Enum
        // Trả về true nếu status là 'active'
        boolean isActive = UserStatus.active.equals(user.getStatus());

        return new CustomUserDetails(
                user.getId(),
                user.getEmail(),
                user.getPasswordHash(),
                isActive,
                Collections.singletonList(authority)
        );
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true; // Tài khoản không bao giờ hết hạn
    }

    @Override
    public boolean isAccountNonLocked() {
        return active; // Tài khoản bị khóa nếu status không phải là active
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true; // Mật khẩu không bao giờ hết hạn
    }

    @Override
    public boolean isEnabled() {
        return active; // Chỉ cho phép đăng nhập nếu status là active
    }
}