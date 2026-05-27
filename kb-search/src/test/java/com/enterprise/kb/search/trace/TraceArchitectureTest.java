package com.enterprise.kb.search.trace;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TraceArchitectureTest {

    @Test
    void ragServicesDoNotDependOnTraceRecorderDtosOrJsonHelpers() throws Exception {
        assertClean("src/main/java/com/enterprise/kb/search/service/impl/CustomerAssistantServiceImpl.java");
        assertClean("src/main/java/com/enterprise/kb/search/service/impl/AfterSalesDomainHandler.java");
        assertClean("src/main/java/com/enterprise/kb/search/service/impl/ComplaintDomainHandler.java");
    }

    @Test
    void turnLifecycleIsOwnedByAop() throws Exception {
        String customer = Files.readString(Path.of(
                "src/main/java/com/enterprise/kb/search/service/impl/CustomerAssistantServiceImpl.java"));
        String aspect = Files.readString(Path.of(
                "src/main/java/com/enterprise/kb/search/trace/aop/TraceTurnAspect.java"));

        assertThat(customer).contains("@TraceTurn(traceType = \"CUSTOMER_ASSISTANT\"");
        assertThat(aspect).contains("@Around(\"@annotation(traceTurn)\")", "traceFacade.start", "trace.complete", "trace.fail");
    }

    private void assertClean(String path) throws Exception {
        String source = Files.readString(Path.of(path));

        assertThat(source).doesNotContain(
                "TraceRecorder",
                "TraceStartRequest",
                "TraceStepRequest",
                "TraceCompleteRequest",
                "TraceStartCommand",
                "traceFacade.start",
                "trace.complete(",
                "trace.fail(",
                "jsonOf(",
                "traceMap(",
                "recordSearchStep(",
                "recordToolStep(",
                "ThreadLocal<UUID>",
                "activeTraceId");
    }
}
