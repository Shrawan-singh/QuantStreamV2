/*
 * ==================================================================================
 * FILE: WebMvcConfig.java
 * ==================================================================================
 *
 * WHAT THIS FILE DOES:
 * Configures "CORS" (Cross-Origin Resource Sharing).
 *
 * WHAT IS CORS?
 * In web development, your frontend dashboard runs on port 3000 (http://localhost:3000),
 * while your backend server runs on port 8080 (http://localhost:8080).
 * By default, web browsers block web pages on port 3000 from fetching data from port 8080
 * for security reasons!
 *
 * This configuration tells web browsers:
 * "It's okay! We trust requests coming from the frontend dashboard. Allow GET, POST,
 * PUT, and DELETE HTTP requests to all `/api/**` routes!"
 * ==================================================================================
 */

package com.quantstream.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS configuration allowing frontend dashboard requests.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        // Allow cross-origin AJAX/fetch calls to all /api/** endpoints
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false);
    }
}
