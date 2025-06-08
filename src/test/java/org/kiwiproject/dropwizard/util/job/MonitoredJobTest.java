package org.kiwiproject.dropwizard.util.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.kiwiproject.base.KiwiEnvironment;
import org.kiwiproject.concurrent.AsyncException;
import org.kiwiproject.test.junit.jupiter.ClearBoxTest;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

@DisplayName("MonitoredJob")
class MonitoredJobTest {

    @Nested
    class Builder {

        @Test
        void shouldRequireName() {
            var builder = MonitoredJob.builder();

            assertThatThrownBy(builder::build)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("name is required");
        }

        @Test
        void shouldRequireTask() {
            var builder = MonitoredJob.builder().name("Name Only Job");

            assertThatThrownBy(builder::build)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("task is required");
        }

        @Test
        void shouldSetDefaults() {
            var job = MonitoredJob.builder()
                    .name("Name and Task Job")
                    .task(() -> System.out.println("Hello"))
                    .build();

            assertAll(
                    () -> assertThat(job.getDecisionFunction()).isNotNull(),
                    () -> assertThat(job.getEnvironment()).isNotNull()
            );
        }
    }

    @Nested
    class Run {

        @SuppressWarnings("removal")
        @Test
        void shouldRunSyncWithoutErrorWhenActive() {
            var environment = mock(KiwiEnvironment.class);
            var mockedTime = System.currentTimeMillis();
            when(environment.currentTimeMillis())
                    .thenReturn(mockedTime)
                    .thenReturn(mockedTime + 1)
                    .thenReturn(mockedTime + 2);

            var taskRunCount = new AtomicInteger();
            var job = MonitoredJob.builder()
                    .name("Run active sync no errors")
                    .task(taskRunCount::incrementAndGet)
                    .environment(environment)
                    .build();

            job.run();

            assertAll(
                    () -> assertThat(taskRunCount.get()).isOne(),
                    () -> assertThat(job.getLastExecutionTime().get()).isOne(),
                    () -> assertThat(job.lastExecutionTimeMillis()).isOne(),
                    () -> assertThat(job.getLastSuccess().get()).isEqualTo(mockedTime + 2),
                    () -> assertThat(job.lastSuccessMillis()).isEqualTo(mockedTime + 2),
                    () -> assertThat(job.getLastFailure().get()).isZero(),
                    () -> assertThat(job.lastFailureMillis()).isZero(),
                    () -> assertThat(job.getFailureCount().get()).isZero(),
                    () -> assertThat(job.failureCount()).isZero(),
                    () -> assertThat(job.getLastJobExceptionInfo()).hasValue(null),
                    () -> assertThat(job.lastJobExceptionInfo()).isNull()
            );
        }

        @SuppressWarnings("removal")
        @Test
        void shouldRunAsyncWithoutErrorWhenActiveAndTimeoutIsSet() {
            var environment = mock(KiwiEnvironment.class);
            var mockedTime = System.currentTimeMillis();
            when(environment.currentTimeMillis())
                    .thenReturn(mockedTime)
                    .thenReturn(mockedTime + 1)
                    .thenReturn(mockedTime + 2);

            var taskRunCount = new AtomicInteger();
            var job = MonitoredJob.builder()
                    .name("Run active async no errors")
                    .task(taskRunCount::incrementAndGet)
                    .environment(environment)
                    .timeout(Duration.ofSeconds(1))
                    .build();

            job.run();
            await().atMost(Duration.ofSeconds(2)).until(() -> taskRunCount.get() > 0);

            assertAll(
                    () -> assertThat(taskRunCount.get()).isOne(),
                    () -> assertThat(job.getLastExecutionTime().get()).isOne(),
                    () -> assertThat(job.lastExecutionTimeMillis()).isOne(),
                    () -> assertThat(job.getLastSuccess().get()).isEqualTo(mockedTime + 2),
                    () -> assertThat(job.lastSuccessMillis()).isEqualTo(mockedTime + 2),
                    () -> assertThat(job.getLastFailure().get()).isZero(),
                    () -> assertThat(job.lastFailureMillis()).isZero(),
                    () -> assertThat(job.getFailureCount().get()).isZero(),
                    () -> assertThat(job.failureCount()).isZero(),
                    () -> assertThat(job.getLastJobExceptionInfo()).hasValue(null),
                    () -> assertThat(job.lastJobExceptionInfo()).isNull()
            );
        }

        @SuppressWarnings("removal")
        @Test
        void shouldSkipExecutionWhenDecisionFunctionReturnsFalse() {
            var environment = mock(KiwiEnvironment.class);
            var mockedTime = System.currentTimeMillis();
            when(environment.currentTimeMillis())
                    .thenReturn(mockedTime);

            var taskRunCount = new AtomicInteger();
            var job = MonitoredJob.builder()
                    .name("Run inactive no errors")
                    .task(taskRunCount::incrementAndGet)
                    .environment(environment)
                    .decisionFunction(monitoredJob -> false)
                    .build();

            job.run();

            assertAll(
                    () -> assertThat(taskRunCount.get()).isZero(),
                    () -> assertThat(job.getLastExecutionTime().get()).isZero(),
                    () -> assertThat(job.lastExecutionTimeMillis()).isZero(),
                    () -> assertThat(job.getLastSuccess().get()).isEqualTo(mockedTime),
                    () -> assertThat(job.lastSuccessMillis()).isEqualTo(mockedTime),
                    () -> assertThat(job.getLastFailure().get()).isZero(),
                    () -> assertThat(job.lastFailureMillis()).isZero(),
                    () -> assertThat(job.getFailureCount().get()).isZero(),
                    () -> assertThat(job.failureCount()).isZero(),
                    () -> assertThat(job.getLastJobExceptionInfo()).hasValue(null),
                    () -> assertThat(job.lastJobExceptionInfo()).isNull()
            );
        }

        @SuppressWarnings("removal")
        @Test
        void shouldTrackErrorWhenTaskFails() {
            var environment = mock(KiwiEnvironment.class);
            var mockedTime = System.currentTimeMillis();
            when(environment.currentTimeMillis())
                    .thenReturn(mockedTime)
                    .thenReturn(mockedTime + 1);

            var job = MonitoredJob.builder()
                    .name("Run active with errors no handler")
                    .task(MonitoredJobTest::throwException)
                    .environment(environment)
                    .build();

            job.run();

            assertAll(
                    () -> assertThat(job.getLastExecutionTime().get()).isZero(),
                    () -> assertThat(job.lastExecutionTimeMillis()).isZero(),
                    () -> assertThat(job.getLastSuccess().get()).isZero(),
                    () -> assertThat(job.lastSuccessMillis()).isZero(),
                    () -> assertThat(job.getLastFailure().get()).isEqualTo(mockedTime + 1),
                    () -> assertThat(job.lastFailureMillis()).isEqualTo(mockedTime + 1),
                    () -> assertThat(job.getFailureCount().get()).isOne(),
                    () -> assertThat(job.failureCount()).isOne(),
                    () -> assertThat(job.getLastJobExceptionInfo()).hasValue(JobExceptionInfo.from(newSampleException())),
                    () -> assertThat(job.lastJobExceptionInfo()).isEqualTo(JobExceptionInfo.from(newSampleException()))
            );
        }

        @SuppressWarnings("removal")
        @Test
        void shouldHandleErrorWhenTaskFailsAndHandlerSet() {
            var environment = mock(KiwiEnvironment.class);
            var mockedTime = System.currentTimeMillis();
            when(environment.currentTimeMillis())
                    .thenReturn(mockedTime)
                    .thenReturn(mockedTime + 1);

            var taskHandledCount = new AtomicInteger();
            var handler = new JobErrorHandler() {
                @Override
                public void handle(MonitoredJob job, Exception exception) {
                    taskHandledCount.incrementAndGet();
                }
            };

            var job = MonitoredJob.builder()
                    .name("Run active with errors and has handler")
                    .task(MonitoredJobTest::throwException)
                    .environment(environment)
                    .errorHandler(handler)
                    .build();

            job.run();

            assertAll(
                    () -> assertThat(job.getLastExecutionTime().get()).isZero(),
                    () -> assertThat(job.lastExecutionTimeMillis()).isZero(),
                    () -> assertThat(job.getLastSuccess().get()).isZero(),
                    () -> assertThat(job.lastSuccessMillis()).isZero(),
                    () -> assertThat(job.getLastFailure().get()).isEqualTo(mockedTime + 1),
                    () -> assertThat(job.lastFailureMillis()).isEqualTo(mockedTime + 1),
                    () -> assertThat(job.getFailureCount().get()).isOne(),
                    () -> assertThat(job.failureCount()).isOne(),
                    () -> assertThat(taskHandledCount.get()).isOne(),
                    () -> assertThat(job.getLastJobExceptionInfo()).hasValue(JobExceptionInfo.from(newSampleException())),
                    () -> assertThat(job.lastJobExceptionInfo()).isEqualTo(JobExceptionInfo.from(newSampleException()))
            );
        }

        @Test
        void shouldNotAllowExceptionsThrownByErrorHandlerToEscape() {
            var handler = new JobErrorHandler() {
                @Override
                public void handle(MonitoredJob job, Exception exception) {
                    throw new RuntimeException("error handling error");
                }
            };

            var job = MonitoredJob.builder()
                    .name("Run active with errors and has handler that throws exception")
                    .task(MonitoredJobTest::throwException)
                    .errorHandler(handler)
                    .build();

            assertThatCode(job::run).doesNotThrowAnyException();
        }
    }

    /**
     * The tests here do not make any assertions, exception that the code does not throw any exceptions.
     * They are here to ensure that Exceptions with and without a cause are both handled.
     * Manually inspect the log messages if you want to verify the actual contents.
     */
    @Nested
    class LogExceptionInfo {

        @ClearBoxTest
        void shouldLogWhenExceptionHasNoCause() {
            var exception = new IOException("I/O operation failed");

            assertThatCode(() -> MonitoredJob.logExceptionInfo(exception, "test-job"))
                    .doesNotThrowAnyException();
        }

        @ClearBoxTest
        void shouldLogWhenExceptionHasRootCause() {
            var executionException = new ExecutionException(new NullPointerException("something was null"));
            var asyncException = new AsyncException("async execution failed", executionException, null);

            assertThatCode(() -> MonitoredJob.logExceptionInfo(asyncException, "test-job"))
                    .doesNotThrowAnyException();
        }
    }

    private static void throwException() {
        throw newSampleException();
    }

    private static RuntimeException newSampleException() {
        return new RuntimeException("oops");
    }
}
