package org.kiwiproject.dropwizard.util.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import io.dropwizard.lifecycle.setup.ScheduledExecutorServiceBuilder;
import io.dropwizard.setup.Environment;
import io.dropwizard.util.Duration;
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kiwiproject.dropwizard.util.config.JobSchedule;
import org.kiwiproject.dropwizard.util.health.MonitoredJobHealthCheck;
import org.kiwiproject.test.dropwizard.mockito.DropwizardMockitoMocks;

@DisplayName("MonitoredJobs")
@ExtendWith(SoftAssertionsExtension.class)
class MonitoredJobsTest {

    private Environment env;

    @BeforeEach
    void setup() {
        env = DropwizardMockitoMocks.mockEnvironment();
    }

    @Nested
    class RegisterJob {

        @Nested
        class ShouldThrowIllegalArgument {

            @BeforeEach
            void setUp() {
                var scheduledExecutorServiceBuilder = mock(ScheduledExecutorServiceBuilder.class);

                when(env.lifecycle().scheduledExecutorService(anyString(), eq(true)))
                        .thenReturn(scheduledExecutorServiceBuilder);

                when(scheduledExecutorServiceBuilder.build()).thenReturn(mock(ScheduledExecutorService.class));
            }

            @Test
            void whenJobWithNameAlreadyRegistered() {
                var jobName = "AlreadyRegistered";

                MonitoredJobs.JOBS.add(jobName);

                assertThatIllegalArgumentException()
                        .isThrownBy(() -> MonitoredJobs.registerJob(env, jobName, new JobSchedule(), null))
                        .withMessage("Jobs cannot be registered more than once with the same name: %s", jobName);
            }

            @Test
            void whenInitialDelayIsMissing() {
                var jobName = "MissingInitialDelay";

                assertThatIllegalArgumentException()
                        .isThrownBy(() -> MonitoredJobs.registerJob(env, jobName,
                                new JobSchedule(null, Duration.seconds(1)), null))
                        .withMessage("Job '%s' must specify a non-null initial delay", jobName);
            }

            @Test
            void whenIntervalDelayIsMissing() {
                var jobName = "MissingIntervalDelay";

                assertThatIllegalArgumentException()
                        .isThrownBy(() -> MonitoredJobs.registerJob(env, jobName,
                                new JobSchedule(Duration.seconds(1), null), null))
                        .withMessage("Job '%s' must specify a non-null interval delay", jobName);
            }
        }

        @Nested
        class ShouldRegisterHealthCheckAndScheduleJob {

            @BeforeEach
            void setUp() {
                MonitoredJobs.JOBS.clear();
            }

            @Test
            void whenArgumentsAreValid_DefaultingDecisionFunctionAndExecutor() {
                var schedule = new JobSchedule(Duration.seconds(10), Duration.seconds(30));
                Runnable task = () -> System.out.println("hello");

                var executor = mock(ScheduledExecutorService.class);
                var scheduledExecutorServiceBuilder = mock(ScheduledExecutorServiceBuilder.class);
                when(env.lifecycle().scheduledExecutorService(anyString(), eq(true)))
                        .thenReturn(scheduledExecutorServiceBuilder);
                when(scheduledExecutorServiceBuilder.build()).thenReturn(executor);

                var monitoredJob = MonitoredJobs.registerJob(env, "ValidJob", schedule, task);

                assertThat(monitoredJob).isNotNull();

                verify(env.healthChecks()).register(eq("Job: ValidJob"), any(MonitoredJobHealthCheck.class));
                verify(executor).scheduleWithFixedDelay(monitoredJob, 10, 30, TimeUnit.SECONDS);
            }

            @Test
            void whenArgumentsAreValid_DefaultingExecutor() {
                var schedule = new JobSchedule(Duration.seconds(10), Duration.seconds(30));
                Runnable task = () -> System.out.println("hello");

                var executor = mock(ScheduledExecutorService.class);
                var scheduledExecutorServiceBuilder = mock(ScheduledExecutorServiceBuilder.class);
                when(env.lifecycle().scheduledExecutorService(anyString(), eq(true)))
                        .thenReturn(scheduledExecutorServiceBuilder);
                when(scheduledExecutorServiceBuilder.build()).thenReturn(executor);

                var monitoredJob = MonitoredJobs.registerJob(env, "ValidJob", schedule, task,
                        (job) -> true);

                assertThat(monitoredJob).isNotNull();

                verify(env.healthChecks()).register(eq("Job: ValidJob"), any(MonitoredJobHealthCheck.class));
                verify(executor).scheduleWithFixedDelay(monitoredJob, 10, 30, TimeUnit.SECONDS);
            }

            @Test
            void whenArgumentsAreValid() {
                var schedule = new JobSchedule(Duration.seconds(10), Duration.seconds(30));
                Runnable task = () -> System.out.println("hello");
                var executor = mock(ScheduledExecutorService.class);

                var monitoredJob = MonitoredJobs.registerJob(env, "ValidJob", schedule, task,
                        (job) -> true, executor);

                assertThat(monitoredJob).isNotNull();

                verify(env.healthChecks()).register(eq("Job: ValidJob"), any(MonitoredJobHealthCheck.class));
                verify(executor).scheduleWithFixedDelay(monitoredJob, 10, 30, TimeUnit.SECONDS);
            }
        }
    }
}
