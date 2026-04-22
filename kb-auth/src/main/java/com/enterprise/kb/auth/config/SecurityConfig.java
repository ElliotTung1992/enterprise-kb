package com.enterprise.kb.auth.config;

import com.enterprise.kb.auth.filter.JwtAuthenticationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.Map;

/**
 * Spring Security 全局安全配置。
 *
 * <p>本配置类负责以下核心安全策略：
 * <ol>
 *   <li><b>无状态会话：</b>禁用 HTTP Session，所有请求通过 JWT Bearer Token 鉴权，
 *       支持水平扩展部署。</li>
 *   <li><b>路由权限：</b>认证接口（{@code /api/v1/auth/**}）、静态前端资源和健康检查接口
 *       对外公开；其余所有接口要求合法 Token。</li>
 *   <li><b>JWT 过滤器：</b>{@link JwtAuthenticationFilter} 在
 *       {@link UsernamePasswordAuthenticationFilter} 之前执行，解析并验证 Token，
 *       将认证信息写入 {@link org.springframework.security.core.context.SecurityContext}。</li>
 *   <li><b>方法级权限：</b>启用 {@code @EnableMethodSecurity}，配合
 *       {@code AppConfig#methodSecurityExpressionHandler} 支持知识空间级别的
 *       {@code @PreAuthorize("hasPermission(...)")} 注解。</li>
 *   <li><b>异常响应：</b>未认证（401）和无权限（403）均以 JSON 格式返回，
 *       而非默认的 HTML 页面，便于前端统一处理。</li>
 *   <li><b>密码编码：</b>统一使用 BCrypt，强度系数默认 10，符合安全规范。</li>
 * </ol>
 *
 * <p><b>CSRF 说明：</b>由于采用无状态 JWT 鉴权，CSRF 攻击向量不适用（无 Cookie 会话），
 * 故显式禁用 CSRF 保护以简化 API 调用。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final UserDetailsService userDetailsService;
    private final ObjectMapper objectMapper;

    /**
     * 配置 HTTP 安全过滤链。
     *
     * <p>安全策略要点：
     * <ul>
     *   <li>禁用 CSRF（无状态 JWT 场景不需要）</li>
     *   <li>会话策略设为 {@link SessionCreationPolicy#STATELESS}，不创建也不使用 HttpSession</li>
     *   <li>公开路由：认证接口、前端静态资源、Actuator 健康检查</li>
     *   <li>其余接口均要求携带有效 JWT</li>
     *   <li>未认证返回 JSON 格式 401，无权限返回 JSON 格式 403</li>
     * </ul>
     *
     * @param http Spring Security 的 {@link HttpSecurity} 构建器
     * @return 构建完成的 {@link SecurityFilterChain}
     * @throws Exception 配置过程中的异常
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/", "/index.html", "/*.html",
                                "/assets/**", "/favicon.ico").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) -> {
                            res.setStatus(HttpStatus.UNAUTHORIZED.value());
                            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            objectMapper.writeValue(res.getWriter(),
                                    Map.of("success", false, "message", "Authentication required"));
                        })
                        .accessDeniedHandler((req, res, e) -> {
                            res.setStatus(HttpStatus.FORBIDDEN.value());
                            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            objectMapper.writeValue(res.getWriter(),
                                    Map.of("success", false, "message", "Access denied"));
                        })
                )
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /**
     * 配置基于数据库用户信息的认证提供者。
     *
     * <p>使用 {@link DaoAuthenticationProvider}，通过 {@link UserDetailsService}
     * 从数据库加载用户信息，并使用 BCrypt 进行密码校验。
     * 该 Provider 被注册到 {@link SecurityFilterChain} 中，处理用户名/密码认证。
     *
     * @return 配置完成的 {@link AuthenticationProvider}
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /**
     * 暴露 {@link AuthenticationManager} Bean，供 {@link com.enterprise.kb.auth.service.AuthService}
     * 在用户名/密码登录时显式调用认证。
     *
     * @param config Spring Security 自动配置的 {@link AuthenticationConfiguration}
     * @return 全局 {@link AuthenticationManager}
     * @throws Exception 获取过程中的异常
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * 配置 BCrypt 密码编码器。
     *
     * <p>BCrypt 默认强度系数为 10，能有效抵御彩虹表攻击和暴力破解。
     * 禁止使用 MD5、SHA1 等不安全算法。
     *
     * @return {@link BCryptPasswordEncoder} 实例
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
