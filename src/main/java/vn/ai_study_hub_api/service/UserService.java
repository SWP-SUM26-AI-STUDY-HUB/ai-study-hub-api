package vn.ai_study_hub_api.service;

import vn.ai_study_hub_api.controller.response.UserResponse;
import java.util.List;

/**
 * Service interface for user management operations.
 */
public interface UserService {

    /**
     * Retrieve all users in the system.
     * @return List of UserResponse objects
     */
    List<UserResponse> getAllUsers();
}
