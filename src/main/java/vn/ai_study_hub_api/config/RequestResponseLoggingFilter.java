package vn.ai_study_hub_api.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;

/**
 * Filter to capture and log HTTP Request and Response bodies for debugging.
 * Bypasses reading body payload for multipart requests (file uploads) to avoid memory overhead and console spam.
 */
@Component
@Slf4j
public class RequestResponseLoggingFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (request instanceof HttpServletRequest && response instanceof HttpServletResponse) {
            HttpServletRequest httpRequest = (HttpServletRequest) request;
            String contentType = httpRequest.getContentType();
            
            // Skip caching body for multipart/form-data (file uploads) to prevent memory issues and console spam
            boolean isMultipart = contentType != null && contentType.startsWith("multipart/");

            if (isMultipart) {
                // If it's a file upload, log basic info and proceed without caching request body
                log.info("Incoming Request: {} {} | Content-Type: {}", httpRequest.getMethod(), httpRequest.getRequestURI(), contentType);
                
                ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper((HttpServletResponse) response);
                chain.doFilter(request, responseWrapper);
                
                String responseBody = new String(responseWrapper.getContentAsByteArray(), responseWrapper.getCharacterEncoding());
                log.info("Outgoing Response: Status {} | Body: {}", responseWrapper.getStatus(), responseBody);
                
                responseWrapper.copyBodyToResponse();
            } else {
                // For JSON/standard requests, cache and log both request and response bodies
                ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(httpRequest, 1024 * 1024);
                ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper((HttpServletResponse) response);

                chain.doFilter(requestWrapper, responseWrapper);

                String requestBody = new String(requestWrapper.getContentAsByteArray(), requestWrapper.getCharacterEncoding());
                String responseBody = new String(responseWrapper.getContentAsByteArray(), responseWrapper.getCharacterEncoding());

                log.info("Incoming Request: {} {} | Body: {}", requestWrapper.getMethod(), requestWrapper.getRequestURI(), requestBody);
                log.info("Outgoing Response: Status {} | Body: {}", responseWrapper.getStatus(), responseBody);

                responseWrapper.copyBodyToResponse();
            }
        } else {
            chain.doFilter(request, response);
        }
    }
}
