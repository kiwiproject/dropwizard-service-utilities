package org.kiwiproject.dropwizard.util.job;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.Runnables;
import io.dropwizard.core.setup.Environment;
import io.dropwizard.lifecycle.setup.ScheduledExecutorServiceBuilder;
import io.dropwizard.util.Duration;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.awaitility.Durations;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.kiwiproject.base.KiwiEnvironment;
import org.kiwiproject.dropwizard.util.concurrent.TestExecutors;
import org.kiwiproject.dropwizard.util.config.JobSchedule;
import org.kiwiproject.dropwizard.util.health.MonitoredJobHealthCheck;
import org.kiwiproject.test.dropwizard.mockito.DropwizardMockitoMocks;

import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

@Slf4j
@DisplayName("MonitoredJobs")
@ExtendWith(SoftAssertionsExtension.class)
class MonitoredJobsTest {

    private Environment env;

    @BeforeEach
    void setUp() {
        env = DropwizardMockitoMocks.mockEnvironment();
    }

    @AfterEach
    void tearDown() {
        MonitoredJobs.JOBS.clear();
    }


    @Nested
    class RegisterJob {

        @Nested
        class ShouldThrowIllegalArgument {

            private Runnable task;
            private JobSchedule schedule;

            @BeforeEach
            void setUp() {
                var scheduledExecutorServiceBuilder = mock(ScheduledExecutorServiceBuilder.class);

                when(env.lifecycle().scheduledExecutorService(anyString(), eq(true)))
                        .thenReturn(scheduledExecutorServiceBuilder);

                when(scheduledExecutorServiceBuilder.build()).thenReturn(mock(ScheduledExecutorService.class));

                task = Runnables.doNothing();

                schedule = JobSchedule.builder()
                        .intervalDelay(Duration.minutes(5))
                        .build();
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

                schedule = JobSchedule.builder()
                        .initialDelay(null)
                        .intervalDelay(Duration.seconds(1))
                        .build();

                assertThatIllegalArgumentException()
                        .isThrownBy(() -> MonitoredJobs.registerJob(env, jobName,
                                schedule, task))
                        .withMessage("Job '%s' must specify a non-null initial delay", jobName);
            }

            @ParameterizedTest
            @ValueSource(ints = {-10, -5, -1})
            void whenInitialDelayIsNegative(int initialDelaySeconds) {
                var jobName = "NegativeInitialDelay";

                schedule = JobSchedule.builder()
                        .initialDelay(Duration.seconds(initialDelaySeconds))
                        .intervalDelay(Duration.seconds(1))
                        .build();

                assertThatIllegalArgumentException()
                        .isThrownBy(() -> MonitoredJobs.registerJob(env, jobName,
                                schedule, task))
                        .withMessage("Job '%s' must specify a non-negative initial delay", jobName);
            }

            @Test
            void whenIntervalDelayIsMissing() {
                var jobName = "MissingIntervalDelay";

                assertThatIllegalArgumentException()
                        .isThrownBy(() -> MonitoredJobs.registerJob(env, jobName,
                                new JobSchedule(), task))
                        .withMessage("Job '%s' must specify a non-null interval delay", jobName);
            }

            @ParameterizedTest
            @ValueSource(ints = {-5, -1, 0})
            void whenIntervalDelayIsNotPositive(long intervalDelaySeconds) {
                var jobName = "NotPositiveIntervalDelay";

                schedule = JobSchedule.ofIntervalDelay(Duration.seconds(intervalDelaySeconds));

                assertThatIllegalArgumentException()
                        .isThrownBy(() -> MonitoredJobs.registerJob(env, jobName,
                                schedule, task))
                        .withMessage("Job '%s' must specify a positive interval delay", jobName);
            }

            @Nested
            class UsingBuilder {

                @Test
                void whenMissingName() {
                    var builder = MonitoredJobs.builder()
                            .task(task)
                            .environment(env)
                            .schedule(schedule);

                    assertThatIllegalArgumentException()
                            .isThrownBy(builder::registerJob)
                            .withMessage("non-blank name is required");
                }

                @Test
                void whenMissingTask() {
                    var builder = MonitoredJobs.builder()
                            .name("test task")
                            .environment(env)
                            .schedule(schedule);

                    assertThatIllegalArgumentException()
                            .isThrownBy(builder::registerJob)
                            .withMessage("task is required");
                }

                @Test
                void whenMissingEnvironment() {
                    var builder = MonitoredJobs.builder()
                            .name("test task")
                            .task(task)
                            .schedule(schedule);

                    assertThatIllegalArgumentException()
                            .isThrownBy(builder::registerJob)
                            .withMessage("environment is required");
                }

                @Test
                void whenMissingSchedule() {
                    var builder = MonitoredJobs.builder()
                            .name("test task")
                            .task(task)
                            .environment(env);

                    assertThatIllegalArgumentException()
                            .isThrownBy(builder::registerJob)
                            .withMessage("schedule is required");
                }
            }
        }

        @Nested
        class ShouldRegisterHealthCheckAndScheduleJob {

            private JobSchedule schedule;
            private Runnable task;
            private ScheduledExecutorService executor;

            @BeforeEach
            void setUp() {
                schedule = JobSchedule.builder()
                        .initialDelay(Duration.seconds(10))
                        .intervalDelay(Duration.seconds(30))
                        .build();

                task = Runnables.doNothing();
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
                        job -> true);

                assertAndVerifyJob(monitoredJob);
            }

            @Test
            void whenArgumentsAreValid() {
                var monitoredJob = MonitoredJobs.registerJob(env, "ValidJob", schedule, task,
                        job -> true, executor);

                assertAndVerifyJob(monitoredJob);
            }

            @Test
            void whenGivenAMonitoredJob_ButNoExecutor() {
                var job = MonitoredJob.builder()
                        .name("ValidJob")
                        .task(task)
                        .build();

                var monitoredJob = MonitoredJobs.registerJob(env, job, schedule);

                assertAndVerifyJob(job);
                assertThat(monitoredJob).isSameAs(job);
            }

            @Test
            void whenGivenAMonitoredJob_AndExecutor() {
                var job = MonitoredJob.builder()
                        .name("ValidJob")
                        .task(task)
                        .build();

                var monitoredJob = MonitoredJobs.registerJob(env, job, schedule, executor);

                assertAndVerifyJob(job);
                assertThat(monitoredJob).isSameAs(job);
            }

            @Test
            void whenUsingBuilder() {
                var kiwiEnv = mock(KiwiEnvironment.class);

                var job = MonitoredJobs.builder()
                        .name("ValidJob")
                        .task(task)
                        .environment(env)
                        .schedule(schedule)
                        .timeout(Duration.seconds(30))
                        .errorHandler(JobErrorHandlers.loggingHandler())
                        .decisionFunction(this::executedMoreThanFiveMinutesAgo)
                        .kiwiEnvironment(kiwiEnv)
                        .registerJob();

                assertAndVerifyJob(job);
            }

            private boolean executedMoreThanFiveMinutesAgo(MonitoredJob job) {
                var now = System.currentTimeMillis();
                var lastExecuted = job.lastExecutionTimeMillis();
                var millisSinceLastExecuted = now - lastExecuted;
                return millisSinceLastExecuted > TimeUnit.MINUTES.toMillis(5);
            }

            @Test
            void whenUsingBuilderWithCustomExecutor() throws InterruptedException {
                var count = new AtomicInteger();
                Runnable countingTask = count::incrementAndGet;
                TestExecutors.use(Executors.newSingleThreadScheduledExecutor(), singleThreadExecutor -> {
                    var fastSchedule = JobSchedule.builder()
                            .initialDelay(Duration.seconds(0))
                            .intervalDelay(Duration.seconds(1))
                            .build();

                    var job = MonitoredJobs.builder()
                            .name("FastJob1234")
                            .task(countingTask)
                            .environment(env)
                            .schedule(fastSchedule)
                            .executor(singleThreadExecutor)
                            .registerJob();

                    await().atMost(Durations.TWO_SECONDS).until(() -> count.get() >= 1);

                    assertThat(job.lastSuccessMillis()).isGreaterThan(Instant.now().minusSeconds(2).toEpochMilli());
                    assertThat(job.lastFailureMillis()).isZero();
                    assertThat(job.failureCount()).isZero();

                    assertHealthCheckWasRegistered(job);
                });
            }

            private void assertAndVerifyJob(MonitoredJob job) {
                assertThat(job).isNotNull();

                assertHealthCheckWasRegistered(job);

                var expectedInitialDelay = schedule.getInitialDelay().toNanoseconds();
                var expectedIntervalDelay = schedule.getIntervalDelay().toNanoseconds();

                verify(executor).scheduleWithFixedDelay(job, expectedInitialDelay, expectedIntervalDelay, TimeUnit.NANOSECONDS);
            }

            private void assertHealthCheckWasRegistered(MonitoredJob job) {
                verify(env.healthChecks())
                        .register(eq("Job: " + job.getName()), any(MonitoredJobHealthCheck.class));
            }
        }
    }

    @Nested
    class RegisteredJobNames {

        private JobSchedule schedule;
        private Runnable task;

        @BeforeEach
        void setUp() {
            var scheduledExecutorServiceBuilder = mock(ScheduledExecutorServiceBuilder.class);

            when(env.lifecycle().scheduledExecutorService(anyString(), eq(true)))
                    .thenReturn(scheduledExecutorServiceBuilder);

            when(scheduledExecutorServiceBuilder.build()).thenReturn(mock(ScheduledExecutorService.class));

            task = Runnables.doNothing();

            schedule = JobSchedule.builder()
                    .intervalDelay(Duration.minutes(5))
                    .build();
        }

        @RepeatedTest(5)
        void shouldClearAndGetRegisteredJobNames() {
            // Register a random number of jobs between 5 and 15 (exclusive).
            // The jobs are registered in a random order.
            var n = ThreadLocalRandom.current().nextInt(5, 15);
            var jobNumbers = IntStream.rangeClosed(1, n).boxed().collect(toList());
            Collections.shuffle(jobNumbers);
            LOG.debug("{} jobNumbers: {}", jobNumbers.size(), jobNumbers);
            jobNumbers.forEach(number ->
                    MonitoredJobs.registerJob(env, "Job" + number, schedule, task));

            var expectedJobNames = jobNumbers.stream()
                    .map(num -> "Job" + num)
                    .sorted()
                    .toArray(String[]::new);

            assertThat(MonitoredJobs.registeredJobNames())
                    .describedAs("job names should be sorted")
                    .containsExactly(expectedJobNames);

            var previouslyRegisteredJobNames = MonitoredJobs.clearRegisteredJobNames();
            assertThat(previouslyRegisteredJobNames)
                    .describedAs("should see sorted job names")
                    .containsExactly(expectedJobNames);

            assertThat(MonitoredJobs.registeredJobNames())
                    .describedAs("job names should be empty after clearing")
                    .isEmpty();
        }

        @Test
        void shouldReturnUnmodifiableSetOfJobNames() {
            MonitoredJobs.registerJob(env, "Job-A", schedule, task);
            MonitoredJobs.registerJob(env, "Job-B", schedule, task);
            MonitoredJobs.registerJob(env, "Job-C", schedule, task);

            var jobNames = MonitoredJobs.registeredJobNames();
            assertThat(jobNames).isUnmodifiable();
        }

        @Test
        void shouldReturnUnmodifiableSetOfJobNames_ForClear() {
            MonitoredJobs.registerJob(env, "Job-A", schedule, task);
            MonitoredJobs.registerJob(env, "Job-B", schedule, task);
            MonitoredJobs.registerJob(env, "Job-C", schedule, task);

            var clearedJobNames = MonitoredJobs.clearRegisteredJobNames();
            assertThat(clearedJobNames).isUnmodifiable();
        }
    }
}
