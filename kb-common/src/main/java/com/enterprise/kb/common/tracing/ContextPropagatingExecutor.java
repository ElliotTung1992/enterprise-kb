package com.enterprise.kb.common.tracing;

import io.micrometer.context.ContextSnapshot;

import java.util.concurrent.Executor;

/**
 * 跨线程上下文传播型执行器（ADR-015 跨线程传播设施化）。
 *
 * <p>把 tracing 上下文（Micrometer {@code ObservationThreadLocalAccessor} 与业务
 * {@link TracingContextHolder}）跨越「跳出 reactor」的异步边界（{@code CompletableFuture} /
 * 普通线程池 / 虚拟线程）搬运到工作线程，使子 span 仍能挂在请求根 span 下并继承业务属性。</p>
 *
 * <p>关键约束：<b>每次任务提交时</b>捕获当前线程上下文（{@code execute} 内 captureAll），
 * 而不是在 executor Bean 创建时捕获一次——后者只会捕获应用启动时的空上下文。这样调用方
 * 只需注入本执行器并正常提交任务，无需在业务调用点手写 {@code ContextSnapshot.captureAll().wrap(...)}。</p>
 *
 * <p>tracing 总开关关闭时不影响正确性：此时没有 observation / holder 注册值，捕获到的快照为空，
 * {@code wrap} 退化为原任务，开销可忽略。</p>
 */
public final class ContextPropagatingExecutor implements Executor {

    private final Executor delegate;

    /**
     * @param delegate 实际执行任务的底层执行器（如虚拟线程执行器）
     */
    public ContextPropagatingExecutor(Executor delegate) {
        this.delegate = delegate;
    }

    /**
     * 在提交时捕获当前线程已注册的全部 thread-local 上下文，包装任务后交由底层执行器执行。
     *
     * @param command 待执行任务
     */
    @Override
    public void execute(Runnable command) {
        ContextSnapshot snapshot = ContextSnapshot.captureAll();
        delegate.execute(snapshot.wrap(command));
    }
}
