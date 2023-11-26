package org.kiwiproject.dropwizard.util.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.kiwiproject.test.assertj.dropwizard.metrics.HealthCheckResultAssertions.assertThatHealthCheck;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.dropwizard.util.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.kiwiproject.base.KiwiEnvironment;
import org.kiwiproject.dropwizard.util.job.JobExceptionInfo;
import org.kiwiproject.dropwizard.util.job.MonitoredJob;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@DisplayName("MonitoredJobHealthCheck")
class MonitoredJobHealthCheckTest {

    private MonitoredJob job;
    private long now;

    @BeforeEach
    void setUp() {
        job = mock(MonitoredJob.class);
        now = System.currentTimeMillis();
    }

    @Nested
    class Builder {

        @Test
        void shouldRequireMonitoredJob() {
            var builder = MonitoredJobHealthCheck.builder();

            assertThatIllegalArgumentException()
                    .isThrownBy(builder::build)
                    .withMessage("job is required");
        }

        @Test
        void shouldRequireExpectedFrequency() {
            var builder = MonitoredJobHealthCheck.builder().job(mock(MonitoredJob.class));

            assertThatIllegalArgumentException()
                    .isThrownBy(builder::build)
                    .withMessage("expectedFrequency is required");
        }

        @Test
        void shouldDefaultVariousFields() {
            var healthCheck = MonitoredJobHealthCheck.builder()
                    .job(job)
                    .expectedFrequency(Duration.seconds(1))
                    .build();

            assertThat(healthCheck.getErrorWarningDuration()).isEqualTo(MonitoredJobHealthCheck.DEFAULT_WARNING_DURATION);
            assertThat(healthCheck.getThresholdFactor()).isEqualTo(MonitoredJobHealthCheck.DEFAULT_THRESHOLD_FACTOR);
            assertThat(healthCheck.getKiwiEnvironment()).isNotNull();
            assertThat(healthCheck.getLowerTimeBound()).isPositive();
        }
    }

    @Nested
    class IsHealthy {

        @Nested
        class WhenNotActive {

            private MonitoredJobHealthCheck healthCheck;

            @BeforeEach
            void setUp() {
                healthCheck = MonitoredJobHealthCheck.builder()
                        .job(job)
                        .expectedFrequency(Duration.seconds(1))
                        .build();
            }

            @Test
            void andHasRunBefore() {
                when(job.getLastSuccess()).thenReturn(new AtomicLong(now));
                when(job.isActive()).thenReturn(false);

                assertHealthyHealthCheck(healthCheck, "Job is inactive. (last run: {})",
                        Instant.ofEpochMilli(now).toString());
            }

            @Test
            void andHasNeverRun() {
                when(job.getLastSuccess()).thenReturn(new AtomicLong());
                when(job.isActive()).thenReturn(false);

                assertHealthyHealthCheck(healthCheck, "Job is inactive. (last run: never)");
            }

        }

        @Nested
        class WhenLastFailureDoesNotMeetThreshold {

            private MonitoredJobHealthCheck healthCheck;

            @BeforeEach
            void setUp() {
                healthCheck = MonitoredJobHealthCheck.builder()
                        .job(job)
                        .expectedFrequency(Duration.seconds(1))
                        .build();
            }

            @Test
            void becauseJobHasNeverFailed() {
                when(job.getLastSuccess()).thenReturn(new AtomicLong(now));
                when(job.isActive()).thenReturn(true);
                when(job.getLastFailure()).thenReturn(new AtomicLong());

                assertHealthyHealthCheck(healthCheck, "Last successful execution was: {}", Instant.ofEpochMilli(now).toString());
            }

            @Test
            void becauseJobFailedOutsideOfThreshold() {
                when(job.getLastSuccess()).thenReturn(new AtomicLong(now));
                when(job.isActive()).thenReturn(true);

                var failureOutsideThreshold = now - MonitoredJobHealthCheck.DEFAULT_WARNING_DURATION.toMilliseconds() - 1;
                when(job.getLastFailure()).thenReturn(new AtomicLong(failureOutsideThreshold));

                assertHealthyHealthCheck(healthCheck, "Last successful execution was: {}", Instant.ofEpochMilli(now).toString());
            }
        }

        @Nested
        class WhenLastRunDoesNotMeetThreshold {

            @Test
            void becauseLastRunIsCloseEnough() {
                when(job.getLastSuccess()).thenReturn(new AtomicLong(now));
                when(job.isActive()).thenReturn(true);
                when(job.getLastFailure()).thenReturn(new AtomicLong());

                var env = mock(KiwiEnvironment.class);
                when(env.currentTimeMillis())
                        .thenReturn(now - MonitoredJobHealthCheck.MINIMUM_WARNING_THRESHOLD.toMilliseconds() - 1)
                        .thenReturn(now);

                var healthCheck = MonitoredJobHealthCheck.builder()
                        .job(job)
                        .expectedFrequency(Duration.seconds(1))
                        .environment(env)
                        .build();

                assertHealthyHealthCheck(healthCheck, "Last successful execution was: {}", Instant.ofEpochMilli(now).toString());
            }

            @Test
            void becauseCheckRanRightAfterStartup_BeforeFirstRun() {
                when(job.getLastSuccess()).thenReturn(new AtomicLong());
                when(job.isActive()).thenReturn(true);
                when(job.getLastFailure()).thenReturn(new AtomicLong());

                var env = mock(KiwiEnvironment.class);
                when(env.currentTimeMillis())
                        .thenReturn(now)
                        .thenReturn(now);

                var healthCheck = MonitoredJobHealthCheck.builder()
                        .job(job)
                        .expectedFrequency(Duration.seconds(1))
                        .environment(env)
                        .build();

                assertHealthyHealthCheck(healthCheck, "Last successful execution was: never");
            }
        }

        private void assertHealthyHealthCheck(MonitoredJobHealthCheck healthCheck, String messageTemplate, Object... args) {
            assertThatHealthCheck(healthCheck)
                    .isHealthy()
                    .hasMessage(messageTemplate, args)
                    .hasDetailsContainingKeys("jobName", "totalErrors", "lastFailure", "lastSuccess",
                            "lastExecutionTimeMs", "expectedFrequencyMs", "errorWarningDurationMs")
                    .hasDetail("warningThresholdMs", MonitoredJobHealthCheck.MINIMUM_WARNING_THRESHOLD.toMilliseconds());
        }
    }

    @Nested
    class IsUnhealthy {

        @Test
        void whenLastFailureIsWithinThreshold() {
            when(job.getLastSuccess()).thenReturn(new AtomicLong(now));
            when(job.isActive()).thenReturn(true);

            var failureOutsideThreshold = now - 1;
            when(job.getLastFailure()).thenReturn(new AtomicLong(failureOutsideThreshold));

            var healthCheck = MonitoredJobHealthCheck.builder()
                    .job(job)
                    .expectedFrequency(Duration.seconds(1))
                    .build();

            assertUnhealthyHealthCheck(healthCheck, "An error has occurred at: {}, which is within the threshold of: {}",
                    Instant.ofEpochMilli(now-1).toString(), MonitoredJobHealthCheck.DEFAULT_WARNING_DURATION);
        }

        @Test
        void whenLastFailureContainsAnException() {
            when(job.getLastSuccess()).thenReturn(new AtomicLong(now));
            when(job.isActive()).thenReturn(true);

            var failureOutsideThreshold = now - 1;
            when(job.getLastFailure()).thenReturn(new AtomicLong(failureOutsideThreshold));
            var jobExceptionInfoReference =
                    new AtomicReference<>(JobExceptionInfo.from(new IOException("unexpected I/O disk error")));
            when(job.getLastJobExceptionInfo()).thenReturn(jobExceptionInfoReference);

            var healthCheck = MonitoredJobHealthCheck.builder()
                    .job(job)
                    .expectedFrequency(Duration.seconds(1))
                    .build();

            assertThatHealthCheck(healthCheck)
                    .isUnhealthy()
                    .hasDetail("lastJobExceptionInfo", jobExceptionInfoReference);
        }

        @Test
        void whenLastRunIsOutsideExpectedFrequency() {
            var lastSuccess = now - MonitoredJobHealthCheck.MINIMUM_WARNING_THRESHOLD.toMilliseconds() - 1;
            when(job.getLastSuccess()).thenReturn(new AtomicLong(lastSuccess));
            when(job.isActive()).thenReturn(true);
            when(job.getLastFailure()).thenReturn(new AtomicLong());

            var env = mock(KiwiEnvironment.class);
            when(env.currentTimeMillis())
                    .thenReturn(now - MonitoredJobHealthCheck.MINIMUM_WARNING_THRESHOLD.toMilliseconds() - 1)
                    .thenReturn(now);

            var healthCheck = MonitoredJobHealthCheck.builder()
                    .job(job)
                    .expectedFrequency(Duration.seconds(1))
                    .environment(env)
                    .build();

            assertUnhealthyHealthCheck(healthCheck, "Last successful execution was: {}, which is older than the threshold of: {} seconds",
                    Instant.ofEpochMilli(lastSuccess).toString(), MonitoredJobHealthCheck.MINIMUM_WARNING_THRESHOLD.toSeconds());

        }

        @Test
        void whenExceptionIsThrown() {
            when(job.getLastSuccess())
                    .thenThrow(new RuntimeException("oops"))
                    .thenReturn(new AtomicLong());

            var healthCheck = MonitoredJobHealthCheck.builder()
                    .job(job)
                    .expectedFrequency(Duration.seconds(1))
                    .build();

            assertUnhealthyHealthCheck(healthCheck, "Encountered failure performing health check");
        }

        @Test
        void whenExceptionIsThrownWhileProcessingException() {
            when(job.getLastSuccess())
                    .thenThrow(new RuntimeException("oops"));

            var healthCheck = MonitoredJobHealthCheck.builder()
                    .job(job)
                    .expectedFrequency(Duration.seconds(1))
                    .build();

            assertThatHealthCheck(healthCheck)
                    .isUnhealthy()
                    .hasMessage("oops");
        }

        private void assertUnhealthyHealthCheck(MonitoredJobHealthCheck healthCheck, String messageTemplate, Object... args) {
            assertThatHealthCheck(healthCheck)
                    .isUnhealthy()
                    .hasMessage(messageTemplate, args)
                    .hasDetailsContainingKeys(
                            "jobName",
                            "totalErrors",
                            "lastFailure",
                            "lastJobExceptionInfo",
                            "lastSuccess",
                            "lastExecutionTimeMs",
                            "expectedFrequencyMs",
                            "errorWarningDurationMs")
                    .hasDetail("warningThresholdMs", MonitoredJobHealthCheck.MINIMUM_WARNING_THRESHOLD.toMilliseconds());
        }
    }
}
