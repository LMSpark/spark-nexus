package com.dsyg.platform;

import com.dsyg.platform.auth.AuthInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final AuthInterceptor authInterceptor;
    private final String corsAllowedOrigins;

    public WebConfig(AuthInterceptor authInterceptor, @Value("${platform.cors.allowed-origins}") String corsAllowedOrigins) {
        this.authInterceptor = authInterceptor;
        this.corsAllowedOrigins = corsAllowedOrigins;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOriginPatterns(parseCsv(corsAllowedOrigins))
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(false);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
            .addPathPatterns("/api/**")
            .excludePathPatterns(
                "/api/auth/login",
                "/api/health",
                "/api/registrations/neighborhood-options",
                "/api/registrations/tenants",
                "/api/registrations/communities",
                "/api/resident/register",
                "/api/payments/*/callback",
                "/api/bank/callbacks/*"
            );
    }

    private String[] parseCsv(String value) {
        if (value == null || value.isBlank()) {
            return new String[] {"http://localhost:*", "http://127.0.0.1:*"};
        }
        return Arrays.stream(value.split(","))
            .map(String::trim)
            .filter(item -> !item.isBlank())
            .toArray(String[]::new);
    }
}
