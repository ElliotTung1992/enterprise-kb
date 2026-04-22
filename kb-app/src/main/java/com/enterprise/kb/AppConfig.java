package com.enterprise.kb;

import com.enterprise.kb.auth.service.AuthService;
import com.enterprise.kb.auth.service.impl.AuthServiceImpl;
import com.enterprise.kb.auth.service.JwtService;
import com.enterprise.kb.auth.service.impl.JwtServiceImpl;
import com.enterprise.kb.user.security.SpacePermissionEvaluator;
import com.enterprise.kb.user.service.UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
public class AppConfig {

    @Bean
    @Primary
    public AuthService authService(
            com.enterprise.kb.auth.mapper.RefreshTokenMapper refreshTokenMapper,
            org.springframework.security.authentication.AuthenticationManager authenticationManager,
            org.springframework.security.core.userdetails.UserDetailsService userDetailsService,
            org.springframework.security.crypto.password.PasswordEncoder passwordEncoder,
            UserService userService) {
        return new AuthServiceImpl(
                jwtService(),
                refreshTokenMapper,
                authenticationManager,
                userDetailsService,
                passwordEncoder,
                userService::createFromRegister,
                userService::getUserIdByUsername,
                userService::updatePasswordHash
        );
    }

    @Bean
    public JwtService jwtService() {
        return new JwtServiceImpl();
    }

    /**
     * Virtual thread executor for async document ingestion.
     */
    @Bean("ingestionExecutor")
    public Executor ingestionExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Register SpacePermissionEvaluator for @PreAuthorize hasPermission().
     */
    @Bean
    public MethodSecurityExpressionHandler methodSecurityExpressionHandler(
            SpacePermissionEvaluator permissionEvaluator) {
        DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
        handler.setPermissionEvaluator(permissionEvaluator);
        return handler;
    }
}
