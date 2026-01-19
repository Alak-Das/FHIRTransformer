package com.fhirtransformer.interceptor;

import org.slf4j.MDC;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;

@Component
public class MdcInterceptor implements HandlerInterceptor {

    private static final String MDC_KEY = "transformerId";
    private static final String HEADER_KEY = "transformerId";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String transformerId = request.getHeader(HEADER_KEY);
        if (transformerId == null || transformerId.isEmpty()) {
            transformerId = UUID.randomUUID().toString();
        }
        MDC.put(MDC_KEY, transformerId);
        // Optionally add it to response header so client knows the ID
        response.setHeader(HEADER_KEY, transformerId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
            @Nullable Exception ex) {
        MDC.clear();
    }
}
