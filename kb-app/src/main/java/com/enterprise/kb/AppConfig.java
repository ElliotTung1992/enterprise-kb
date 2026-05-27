package com.enterprise.kb;

import com.enterprise.kb.auth.service.AuthService;
import com.enterprise.kb.auth.service.impl.AuthServiceImpl;
import com.enterprise.kb.auth.service.JwtService;
import com.enterprise.kb.auth.service.impl.JwtServiceImpl;
import com.enterprise.kb.user.security.SpacePermissionEvaluator;
import com.enterprise.kb.user.service.UserService;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
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
 *   <li>{@code traceExecutor}：基于 JDK 21 虚拟线程的异步执行器，用于 Agent Trace step 写入。</li>
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
     * 基于 JDK 21 虚拟线程的异步执行器，专用于 Agent Trace step 写入。
     *
     * <p>Trace 外壳创建与完成状态更新保持同步，细粒度 step 写入走该执行器做 best-effort
     * 持久化，避免排障日志写入放大用户请求延迟。</p>
     *
     * @return 虚拟线程执行器
     */
    @Bean("traceExecutor")
    public Executor traceExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Markdown 结构感知 RAG 专用 Milvus 集合。
     *
     * <p>标准 RAG 竖井退役后，{@code md_kb_chunks} 是系统唯一的 Milvus 集合。
     * 入库和查询均通过 {@code mdVectorStore} 显式注入。</p>
     *
     * @param milvusClient   Milvus 客户端
     * @param embeddingModel 默认 embedding 模型
     * @param databaseName   Milvus database
     * @param collectionName Markdown 专用集合名
     * @param dimension      embedding 维度
     * @return Markdown 专用向量存储
     */
    @Bean("mdVectorStore")
    public VectorStore mdVectorStore(
            MilvusServiceClient milvusClient,
            @Qualifier("dashscopeEmbeddingModel") EmbeddingModel embeddingModel,
            @Value("${spring.ai.vectorstore.milvus.database-name:default}") String databaseName,
            @Value("${enterprise.kb.milvus.md-collection:md_kb_chunks}") String collectionName,
            @Value("${spring.ai.vectorstore.milvus.embedding-dimension:1536}") int dimension) {
        return MilvusVectorStore.builder(milvusClient, embeddingModel)
                .databaseName(databaseName)
                .collectionName(collectionName)
                .embeddingDimension(dimension)
                .indexType(IndexType.IVF_FLAT)
                .metricType(MetricType.COSINE)
                .initializeSchema(true)
                .build();
    }

    /**
     * 应用自定义的 Milvus 客户端。
     *
     * <p>标准 RAG 竖井退役后，已通过 {@code spring.autoconfigure.exclude} 排除 Spring AI 的
     * {@code MilvusVectorStoreAutoConfiguration}——该自动配置会创建标准集合 {@code kb_chunks}
     * 并启用 {@code initialize-schema} 自动建表。由于它同时负责创建 {@link MilvusServiceClient}，
     * 这里改为手动声明客户端，供 {@code mdVectorStore} 注入。</p>
     *
     * @param host         Milvus 主机
     * @param port         Milvus 端口
     * @param databaseName Milvus database
     * @return Milvus 客户端
     */
    @Bean
    public MilvusServiceClient milvusClient(
            @Value("${spring.ai.vectorstore.milvus.client.host:localhost}") String host,
            @Value("${spring.ai.vectorstore.milvus.client.port:19530}") int port,
            @Value("${spring.ai.vectorstore.milvus.database-name:default}") String databaseName) {
        return new MilvusServiceClient(ConnectParam.newBuilder()
                .withHost(host)
                .withPort(port)
                .withDatabaseName(databaseName)
                .build());
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
