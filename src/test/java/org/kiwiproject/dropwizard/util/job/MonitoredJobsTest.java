package org.kiwiproject.dropwizard.util.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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

            private Runnable task;

            @BeforeEach
            void setUp() {
                var scheduledExecutorServiceBuilder = mock(ScheduledExecutorServiceBuilder.class);

                when(env.lifecycle().scheduledExecutorService(anyString(), eq(true)))
                        .thenReturn(scheduledExecutorServiceBuilder);

                when(scheduledExecutorServiceBuilder.build()).thenReturn(mock(ScheduledExecutorService.class));

                task = () -> System.out.println("hello");
            }

            @Test
            void whenJobWithNameAlreadyRegistered() {
                var jobName = "AlreadyRegistered";

                MonitoredJobs.JOBS.add(jobName);

                assertThatIllegalArgumentException()
                        .isThrownBy(() -> MonitoredJobs.registerJob(env, jobName, new JobSchedule(), task))
                        .withMessage("Jobs cannot be registered more than once with the same name: %s", jobName);
            }

            @Test
            void whenInitialDelayIsMissing() {
                var jobName = "MissingInitialDelay";

                var schedule = JobSchedule.builder()
                        .initialDelay(null)
                        .intervalDelay(Duration.seconds(1))
                        .build();

                assertThatIllegalArgumentException()
                        .isThrownBy(() -> MonitoredJobs.registerJob(env, jobName,
                                schedule, task))
                        .withMessage("Job '%s' must specify a non-null initial delay", jobName);
            }

            @Test
            void whenIntervalDelayIsMissing() {
                var jobName = "MissingIntervalDelay";

                assertThatIllegalArgumentException()
                        .isThrownBy(() -> MonitoredJobs.registerJob(env, jobName,
                                new JobSchedule(), task))
                        .withMessage("Job '%s' must specify a non-null interval delay", jobName);
            }
        }

        @Nested
        class ShouldRegisterHealthCheckAndScheduleJob {

            private JobSchedule schedule;
            private Runnable task;
            private ScheduledExecutorService executor;

            @BeforeEach
            void setUp() {
                MonitoredJobs.JOBS.clear();

                schedule = JobSchedule.builder()
                        .initialDelay(Duration.seconds(10))
                        .intervalDelay(Duration.seconds(30))
                        .build();

                task = () -> System.out.println("hello");
                executor = mock(ScheduledExecutorService.class);

                var scheduledExecutorServiceBuilder = mock(ScheduledExecutorServiceBuilder.class);
                when(env.lifecycle().scheduledExecutorService(anyString(), eq(true)))
                        .thenReturn(scheduledExecutorServiceBuilder);
                when(scheduledExecutorServiceBuilder.build()).thenReturn(executor);
            }

            @Test
            void whenArgumentsAreValid_DefaultingDecisionFunctionAndExecutor() {
                var monitoredJob = MonitoredJobs.registerJob(env, "ValidJob", schedule, task);
                assertAndVerifyJob(monitoredJob);
            }

            @Test
            void whenArgumentsAreValid_DefaultingExecutor() {
                var monitoredJob = MonitoredJobs.registerJob(env, "ValidJob", schedule, task,
                        (job) -> true);

                assertAndVerifyJob(monitoredJob);
            }

            @Test
            void whenArgumentsAreValid() {
                var monitoredJob = MonitoredJobs.registerJob(env, "ValidJob", schedule, task,
                        (job) -> true, executor);

                assertAndVerifyJob(monitoredJob);
            }

            @Test
            void whenGivenAMonitoredJob() {
                var job = MonitoredJob.builder()
                        .name("ValidJob")
                        .task(task)
                        .build();

                var monitoredJob = MonitoredJobs.registerJob(env, job, schedule, executor);

                assertAndVerifyJob(job);
                assertThat(monitoredJob).isSameAs(job);
            }

            private void assertAndVerifyJob(MonitoredJob job) {
                assertThat(job).isNotNull();

                verify(env.healthChecks()).register(eq("Job: ValidJob"), any(MonitoredJobHealthCheck.class));
                verify(executor).scheduleWithFixedDelay(job, 10, 30, TimeUnit.SECONDS);
            }
        }
    }
}
