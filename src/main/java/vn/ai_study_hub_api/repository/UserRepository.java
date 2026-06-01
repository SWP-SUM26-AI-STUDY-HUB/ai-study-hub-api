package vn.ai_study_hub_api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.ai_study_hub_api.model.UserEntity;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for managing UserEntity objects.
 */
@Repository
public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    /**
     * Find a user by email address.
     * @param email User email
     * @return Optional containing found user or empty
     */
    Optional<UserEntity> findByEmail(String email);
    boolean existsByEmail(String email);
    
}
