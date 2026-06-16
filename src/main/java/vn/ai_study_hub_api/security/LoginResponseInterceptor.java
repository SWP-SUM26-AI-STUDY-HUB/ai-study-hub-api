package vn.ai_study_hub_api.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import vn.ai_study_hub_api.common.ApiResponse;
import vn.ai_study_hub_api.controller.response.LoginResponse;
import vn.ai_study_hub_api.service.UserSanctionService;

@ControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class LoginResponseInterceptor implements ResponseBodyAdvice<Object> {

    private final UserSanctionService userSanctionService;

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return returnType.getParameterType().equals(ApiResponse.class);
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        try {
            if (body instanceof ApiResponse<?> apiResponse) {
                if (apiResponse.isSuccess() && apiResponse.getData() instanceof LoginResponse loginResponse) {
                    if (loginResponse.getAccessToken() != null && loginResponse.getId() != null) {
                        userSanctionService.trackUserToken(loginResponse.getId(), loginResponse.getAccessToken());
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("Failed to intercept and track active session token from login response", ex);
        }
        return body;
    }
}
