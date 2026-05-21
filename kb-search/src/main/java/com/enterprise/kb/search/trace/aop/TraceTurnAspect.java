package com.enterprise.kb.search.trace.aop;

import com.enterprise.kb.common.util.SecurityUtils;
import com.enterprise.kb.search.dto.CustomerAssistantRequest;
import com.enterprise.kb.search.dto.CustomerAssistantResponse;
import com.enterprise.kb.search.dto.QnARequest;
import com.enterprise.kb.search.dto.QnAResponse;
import com.enterprise.kb.search.trace.TraceContextHolder;
import com.enterprise.kb.search.trace.TraceFacade;
import com.enterprise.kb.search.trace.TracePayload;
import com.enterprise.kb.search.trace.TraceScope;
import com.enterprise.kb.search.trace.TraceStartCommand;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * turn 级 Trace 生命周期切面。
 */
@Aspect
@Component
@RequiredArgsConstructor
public class TraceTurnAspect {

    private final TraceFacade traceFacade;

    /**
     * 包裹被 {@link TraceTurn} 标记的方法，负责 start/bind/complete/fail。
     *
     * @param joinPoint 连接点
     * @param traceTurn 注解配置
     * @return 原方法返回值
     * @throws Throwable 原方法异常
     */
    @Around("@annotation(traceTurn)")
    public Object aroundTraceTurn(ProceedingJoinPoint joinPoint, TraceTurn traceTurn) throws Throwable {
        TraceTurnMetadata metadata = metadataOf(joinPoint.getArgs(), traceTurn);
        TraceScope trace = traceFacade.start(new TraceStartCommand(
                traceTurn.traceType(),
                metadata.agentName(),
                metadata.sessionId(),
                metadata.spaceId(),
                currentUserIdOrNull(),
                null,
                metadata.modelProvider(),
                metadata.inputText(),
                metadata.rawInput()));

        try (AutoCloseable ignored = TraceContextHolder.bind(trace)) {
            Object result = joinPoint.proceed(metadata.args());
            trace.complete(outputOf(result), tokensUsedOf(result));
            return result;
        } catch (Throwable e) {
            trace.fail(e);
            throw e;
        }
    }

    private TraceTurnMetadata metadataOf(Object[] originalArgs, TraceTurn traceTurn) {
        Object[] args = originalArgs.clone();
        UUID spaceId = null;
        UUID sessionId = null;
        String modelProvider = null;
        String inputText = null;
        Object rawInput = TracePayload.map("args", args);
        String agentName = traceTurn.agentName();

        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            if (arg instanceof UUID uuid && spaceId == null) {
                spaceId = uuid;
            } else if (arg instanceof QnARequest req) {
                sessionId = req.sessionId() != null ? req.sessionId() : UUID.randomUUID();
                QnARequest prepared = req.sessionId() == null
                        ? new QnARequest(req.question(), sessionId, req.modelProvider(), req.modelName(), req.topK())
                        : req;
                args[i] = prepared;
                modelProvider = prepared.modelProvider();
                inputText = prepared.question();
                rawInput = TracePayload.map(
                        "question", prepared.question(),
                        "sessionId", prepared.sessionId(),
                        "spaceId", spaceId,
                        "modelProvider", prepared.modelProvider(),
                        "topK", prepared.topK());
            } else if (arg instanceof CustomerAssistantRequest req) {
                sessionId = req.sessionId() != null ? req.sessionId() : UUID.randomUUID();
                CustomerAssistantRequest prepared = req.sessionId() == null
                        ? new CustomerAssistantRequest(sessionId, req.message(), req.modelProvider())
                        : req;
                args[i] = prepared;
                modelProvider = prepared.modelProvider();
                inputText = prepared.message();
                rawInput = TracePayload.map(
                        "message", prepared.message(),
                        "sessionId", prepared.sessionId(),
                        "modelProvider", prepared.modelProvider());
            }
        }
        return new TraceTurnMetadata(args, agentName, sessionId, spaceId, modelProvider, inputText, rawInput);
    }

    private Object outputOf(Object result) {
        if (result instanceof QnAResponse response) {
            return TracePayload.map(
                    "answer", response.answer(),
                    "sessionId", response.sessionId(),
                    "citations", response.citations(),
                    "modelUsed", response.modelUsed());
        }
        if (result instanceof CustomerAssistantResponse response) {
            return TracePayload.map("answer", response.answer(), "sessionId", response.sessionId());
        }
        return result;
    }

    private Integer tokensUsedOf(Object result) {
        if (result instanceof QnAResponse response) {
            return response.tokensUsed();
        }
        return null;
    }

    private UUID currentUserIdOrNull() {
        try {
            return SecurityUtils.getCurrentUserId();
        } catch (Exception e) {
            return null;
        }
    }

    private record TraceTurnMetadata(
            Object[] args,
            String agentName,
            UUID sessionId,
            UUID spaceId,
            String modelProvider,
            String inputText,
            Object rawInput
    ) {}
}
