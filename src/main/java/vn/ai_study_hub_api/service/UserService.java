package vn.ai_study_hub_api.service;

import vn.ai_study_hub_api.controller.response.UserResponse;
import java.util.List;

public interface UserService {


    List<UserResponse> getAllUsers();
}
