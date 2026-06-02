package com.enterprise.kb.common.tracing;

/**
 * LangFuse / OTel span 业务属性 key 常量（ADR-015）。
 *
 * <p>分两类：</p>
 * <ul>
 *   <li><b>trace 级 attribute</b>：LangFuse 原生识别的 {@code langfuse.user.id} /
 *       {@code langfuse.session.id}，用于在 LangFuse 中按用户 / 会话过滤聚合。
 *       二者为高基数字段（每用户 / 每会话唯一），不作为 low-cardinality tag。</li>
 *   <li><b>metadata attribute</b>：以 {@code langfuse.trace.metadata.*} 前缀挂业务维度，
 *       如 {@code space_id} / {@code model_provider} / {@code document_id}。</li>
 * </ul>
 *
 * <p>这些属性先挂在 service 入口的业务根 span，再由
 * {@code LangfuseChildAttributeSpanProcessor} 经 {@link TracingContextHolder} 复制到
 * 同一 trace 的 LLM / tool / retrieval 子 span，避免只在根 span 可见导致 observation 级过滤失效。</p>
 */
public final class TracingAttributes {

    /** LangFuse 用户维度（高基数）。 */
    public static final String LANGFUSE_USER_ID = "langfuse.user.id";
    /** LangFuse 会话维度（高基数）。 */
    public static final String LANGFUSE_SESSION_ID = "langfuse.session.id";

    /** 知识空间 ID（metadata）。 */
    public static final String META_SPACE_ID = "langfuse.trace.metadata.space_id";
    /** 模型 provider（metadata）。 */
    public static final String META_MODEL_PROVIDER = "langfuse.trace.metadata.model_provider";
    /** 文档 ID（入库 trace 用，metadata）。 */
    public static final String META_DOCUMENT_ID = "langfuse.trace.metadata.document_id";

    /**
     * trace / observation 的 input、output 与 metadata 属性 key（design-langfuse-io-mapping D1）。
     *
     * <p>LangFuse OTLP ingestion 直接识别下列原生 namespace：写在 trace 根 span 的
     * {@code langfuse.trace.input/output} 抬升为 trace 级 input/output；写在任意 span 的
     * {@code langfuse.observation.input/output} 作为该 observation 的 input/output。
     * metadata 以 {@code langfuse.trace.metadata.*} / {@code langfuse.observation.metadata.*} 前缀挂业务维度。</p>
     */
    /** trace 级 input（写在根 span）。 */
    public static final String TRACE_INPUT = "langfuse.trace.input";
    /** trace 级 output（写在根 span）。 */
    public static final String TRACE_OUTPUT = "langfuse.trace.output";
    /** observation 级 input（写在任意 span）。 */
    public static final String OBSERVATION_INPUT = "langfuse.observation.input";
    /** observation 级 output（写在任意 span）。 */
    public static final String OBSERVATION_OUTPUT = "langfuse.observation.output";
    /** trace 级 metadata 前缀。 */
    public static final String TRACE_METADATA_PREFIX = "langfuse.trace.metadata.";
    /** observation 级 metadata 前缀。 */
    public static final String OBSERVATION_METADATA_PREFIX = "langfuse.observation.metadata.";

    private TracingAttributes() {
    }
}
