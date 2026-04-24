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

/**
 * 应用全局 Bean 装配配置。
 *
 * <p>kb-app 是多模块项目的启动入口，负责将各子模块中存在循环依赖或跨模块引用的 Bean
 * 集中在此处手动装配，避免在子模块中引入不必要的模块间依赖。
 *
 * <ul>
 *   <li>{@link AuthService} / {@link JwtService}：认证服务，需注入来自 kb-user 的
 *       {@link com.enterprise.kb.user.service.UserService}，故在此处显式构造。</li>
 *   <li>{@code ingestionExecutor}：基于 JDK 21 虚拟线程的异步执行器，用于文档摄入流水线。</li>
 *   <li>{@link MethodSecurityExpressionHandler}：注册自定义权限评估器，
 *       使 {@code @PreAuthorize("hasPermission(...)")} 能够感知知识空间级别的权限。</li>
 * </ul>
 */
@Configuration
public class AppConfig {

    /**
     * 手动装配 {@link AuthService} 实现，注入跨模块依赖。
     *
     * <p>由于 {@link AuthServiceImpl} 需要同时依赖 kb-auth 内部组件和 kb-user 的
     * {@link com.enterprise.kb.user.service.UserService}，直接在 kb-auth 中声明 Bean
     * 会引入反向模块依赖，因此在启动模块统一装配。
     *
     * @param refreshTokenMapper      Refresh Token 持久化 Mapper
     * @param authenticationManager   Spring Security 认证管理器
     * @param userDetailsService      用户详情加载服务
     * @param passwordEncoder         BCrypt 密码编码器
     * @param userService             用户业务服务，用于注册时创建用户及查询用户 ID
     * @return 配置完成的 {@link AuthService} 实例
     */
    @Bean
    @Primary
    public AuthService authService(
            com.enterprise.kb.auth.mapper.RefreshTokenMapper refreshTokenMapper,
            org.springframework.security.authentication.AuthenticationManager authenticationManager,
            org.springframework.security.core.userdetails.UserDetailsService userDetailsService,
            org.springframework.security.crypto.password.PasswordEncoder passwordEncoder,
            UserService userService,
            com.enterprise.kb.user.mapper.McpApiKeyMapper mcpApiKeyMapper) {
        return new AuthServiceImpl(
                jwtService(),
                refreshTokenMapper,
                authenticationManager,
                userDetailsService,
                passwordEncoder,
                userService::createFromRegister,
                userService::getUserIdByUsername,
                userService::updatePasswordHash,
                keyHash -> mcpApiKeyMapper.findUserIdByKeyHash(keyHash),
                userId -> userService.getUserById(userId).username()
        );
    }

    /**
     * 创建 {@link JwtService} 实例，供 {@link #authService} 注入使用。
     *
     * <p>单独声明为 Bean，以便在需要时被其他组件（如过滤器）直接注入。
     *
     * @return {@link JwtServiceImpl} 实例
     */
    @Bean
    public JwtService jwtService() {
        return new JwtServiceImpl();
    }

    /**
     * 基于 JDK 21 虚拟线程的异步执行器，专用于文档摄入流水线。
     *
     * <p>虚拟线程在 I/O 密集型任务（解析、向量化、数据库写入）中可大幅提升并发吞吐量，
     * 且无需手动管理线程池大小。每个摄入任务独占一个虚拟线程，互不阻塞。
     *
     * @return 虚拟线程执行器
     */
    @Bean("ingestionExecutor")
    public Executor ingestionExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * 注册自定义方法级安全表达式处理器，使 {@code @PreAuthorize("hasPermission(...)")}
     * 能够调用 {@link SpacePermissionEvaluator} 进行知识空间级别的权限校验。
     *
     * <p>若不在此处覆盖默认的 {@link MethodSecurityExpressionHandler}，
     * {@code hasPermission()} 表达式将始终返回 {@code false}。
     *
     * @param permissionEvaluator 知识空间权限评估器
     * @return 配置了自定义评估器的表达式处理器
     */
    @Bean
    public MethodSecurityExpressionHandler methodSecurityExpressionHandler(
            SpacePermissionEvaluator permissionEvaluator) {
        DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
        handler.setPermissionEvaluator(permissionEvaluator);
        return handler;
    }
}
