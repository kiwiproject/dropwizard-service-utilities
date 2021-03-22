package org.kiwiproject.dropwizard.util.job;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

@DisplayName("JobErrorHandlers")
@Slf4j
class JobErrorHandlersTest {

    @Test
    void shouldCreateNoOpHandler() {
        var handler = JobErrorHandlers.noOpHandler();
        assertThatCode(() -> handler.handle(null, null))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldCreateLoggingHandler() {
        var handler = JobErrorHandlers.loggingHandler();

        var job = MonitoredJob.builder()
                .name("test task")
                .task(() -> LOG.info("Executing task"))
                .build();

        assertThatCode(() -> handler.handle(job, new RuntimeException("an oop happened")))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldCreateLoggingHandler_UsingCustomLogger() {
        var logger = mock(Logger.class);
        var handler = JobErrorHandlers.loggingHandler(logger);

        var job = MonitoredJob.builder()
                .name("test task")
                .task(() -> LOG.info("Executing task"))
                .build();

        var exception = new RuntimeException("an oop happened");
        assertThatCode(() -> handler.handle(job, exception))
                .doesNotThrowAnyException();

        verify(logger).warn("Job '{}' threw an exception", "test task", exception);
    }
}
