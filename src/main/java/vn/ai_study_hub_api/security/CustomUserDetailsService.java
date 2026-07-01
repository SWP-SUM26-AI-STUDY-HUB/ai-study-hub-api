package vn.ai_study_hub_api.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.ai_study_hub_api.model.StoragePlanEntity;
import vn.ai_study_hub_api.model.UserEntity;
import vn.ai_study_hub_api.model.UserStatus;
import vn.ai_study_hub_api.repository.StoragePlanRepository;
import vn.ai_study_hub_api.repository.UserRepository;


@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final StoragePlanRepository storagePlanRepository;

    @Autowired
    public CustomUserDetailsService(UserRepository userRepository, StoragePlanRepository storagePlanRepository) {
        this.userRepository = userRepository;
        this.storagePlanRepository = storagePlanRepository;
    }

    @Override
    @Transactional
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));
        
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        if (user.getPlanId() != null && user.getPlanId() != 1 
                && user.getPlanExpiresAt() != null && user.getPlanExpiresAt().isBefore(now)) {
            
            user.setPlanId(1);
            user.setPlanExpiresAt(null);
            
            long freeLimitInBytes = 2L * 1024L * 1024L * 1024L;
            StoragePlanEntity freePlan = storagePlanRepository.findById(1).orElse(null);
            if (freePlan != null) {
                freeLimitInBytes = freePlan.getStorageLimit();
            }
            
            long used = user.getStorageUsed() != null ? user.getStorageUsed() : 0L;
            if (used > freeLimitInBytes) {
                user.setStatus(UserStatus.OVERLIMITSTORAGE);
            }
            
            user = userRepository.save(user);
        }
        
        return CustomUserDetails.build(user);
    }
}
