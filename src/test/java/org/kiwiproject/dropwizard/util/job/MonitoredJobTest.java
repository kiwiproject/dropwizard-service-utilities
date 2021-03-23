package org.kiwiproject.dropwizard.util.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.assertj.core.api.SoftAssertions;
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kiwiproject.base.KiwiEnvironment;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

@DisplayName("MonitoredJob")
@ExtendWith(SoftAssertionsExtension.class)
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

            assertThat(job.getDecisionFunction()).isNotNull();
            assertThat(job.getEnvironment()).isNotNull();
        }
    }

    @Nested
    class Run {

        @Test
        void shouldRunSyncWithoutErrorWhenActive(SoftAssertions softly) {
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

            softly.assertThat(taskRunCount.get()).isOne();
            softly.assertThat(job.getLastExecutionTime().get()).isOne();
            softly.assertThat(job.getLastSuccess().get()).isEqualTo(mockedTime + 2);
            softly.assertThat(job.getLastFailure().get()).isZero();
            softly.assertThat(job.getFailureCount().get()).isZero();
        }

        @Test
        void shouldRunAsyncWithoutErrorWhenActiveAndTimeoutIsSet(SoftAssertions softly) {
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

            softly.assertThat(taskRunCount.get()).isOne();
            softly.assertThat(job.getLastExecutionTime().get()).isOne();
            softly.assertThat(job.getLastSuccess().get()).isEqualTo(mockedTime + 2);
            softly.assertThat(job.getLastFailure().get()).isZero();
            softly.assertThat(job.getFailureCount().get()).isZero();
        }

        @Test
        void shouldSkipExecutionWhenDecisionFunctionReturnsFalse(SoftAssertions softly) {
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

            softly.assertThat(taskRunCount.get()).isZero();
            softly.assertThat(job.getLastExecutionTime().get()).isZero();
            softly.assertThat(job.getLastSuccess().get()).isEqualTo(mockedTime);
            softly.assertThat(job.getLastFailure().get()).isZero();
            softly.assertThat(job.getFailureCount().get()).isZero();
        }

        @Test
        void shouldTrackErrorWhenTaskFails(SoftAssertions softly) {
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

            softly.assertThat(job.getLastExecutionTime().get()).isZero();
            softly.assertThat(job.getLastSuccess().get()).isZero();
            softly.assertThat(job.getLastFailure().get()).isEqualTo(mockedTime + 1);
            softly.assertThat(job.getFailureCount().get()).isOne();
        }

        @Test
        void shouldHandleErrorWhenTaskFailsAndHandlerSet(SoftAssertions softly) {
            var environment = mock(KiwiEnvironment.class);
            var mockedTime = System.currentTimeMillis();
            when(environment.currentTimeMillis())
                    .thenReturn(mockedTime)
                    .thenReturn(mockedTime + 1);

            var taskHandledCount = new AtomicInteger();
            var handler = new JobErrorHandler() {
                @Override
                public void handle(MonitoredJob job, Throwable throwable) {
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

            softly.assertThat(job.getLastExecutionTime().get()).isZero();
            softly.assertThat(job.getLastSuccess().get()).isZero();
            softly.assertThat(job.getLastFailure().get()).isEqualTo(mockedTime + 1);
            softly.assertThat(job.getFailureCount().get()).isOne();
            softly.assertThat(taskHandledCount.get()).isOne();
        }

        @Test
        void shouldNotAllowExceptionsThrownByErrorHandlerToEscape() {
            var handler = new JobErrorHandler() {
                @Override
                public void handle(MonitoredJob job, Throwable throwable) {
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

    private static void throwException() {
        throw new RuntimeException("oops");
    }
}
