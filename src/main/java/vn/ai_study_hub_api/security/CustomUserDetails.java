package vn.ai_study_hub_api.security;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import vn.ai_study_hub_api.model.UserEntity;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;

/**
 * Custom UserDetails implementation holding authenticated UserEntity details.
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
     * Map UserEntity into CustomUserDetails instance.
     * @param user UserEntity source
     * @return CustomUserDetails mapped instance
     */
    public static CustomUserDetails build(UserEntity user) {
        String roleStr = user.getRole().toUpperCase();
        GrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + roleStr);
        
        boolean isActive = "active".equalsIgnoreCase(user.getStatus());

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
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return active;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
