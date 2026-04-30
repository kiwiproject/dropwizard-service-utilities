package org.kiwiproject.dropwizard.util.job;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.isNull;
import static org.kiwiproject.base.KiwiPreconditions.checkArgument;
import static org.kiwiproject.base.KiwiPreconditions.checkArgumentNotBlank;
import static org.kiwiproject.base.KiwiPreconditions.checkArgumentNotNull;
import static org.kiwiproject.base.KiwiStrings.f;
import static org.kiwiproject.dropwizard.util.lifecycle.StandardLifecycles.newScheduledExecutor;

import com.google.common.annotations.VisibleForTesting;
import io.dropwizard.core.setup.Environment;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.kiwiproject.base.KiwiEnvironment;
import org.kiwiproject.dropwizard.util.config.JobSchedule;
import org.kiwiproject.dropwizard.util.health.MonitoredJobHealthCheck;

import java.time.Duration;
import java.util.Collections;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * A set of utilities to assist in setting up {@link MonitoredJob} instances with associated
 * {@link MonitoredJobHealthCheck} health checks.
 * <p>
 * There are convenience static factory methods to create and register {@link MonitoredJob} instances
 * using default health check configuration. If none of them suits your requirements, or if you need
 * to customize health check parameters such as {@code errorWarningDuration}, {@code thresholdFactor},
 * or {@code suppressWarningThreshold}, use the {@link #builder()} instead.
 * 
 * @see MonitoredJob
 * @see MonitoredJobHealthCheck
 */
@Slf4j
@UtilityClass
public class MonitoredJobs {

    /**
     * The job names that have been registered.
     *
     * @implNote When using the no-args constructor, {@link ConcurrentSkipListSet} maintains a
     * natural sort order on its elements, so we don't need to perform any explicit sorting
     * when returning the job names.
     */
    @VisibleForTesting
    static final SortedSet<String> JOBS = new ConcurrentSkipListSet<>();

    /**
     * Create a new {@link MonitoredJob}, set up the {@link MonitoredJobHealthCheck}, and schedule the job on the given
     * {@link Environment}, with the given name, schedule, and runnable.
     *
     * @param env      the Dropwizard environment to register the health check and schedule the job
     * @param name     the name of the job
     * @param schedule the schedule for the job
     * @param runnable the task that will be run inside the Monitored Job
     * @return the new {@link MonitoredJob}
     * @throws IllegalArgumentException if a job with {@code name} has already been registered
     */
    public static MonitoredJob registerJob(Environment env, String name, JobSchedule schedule, Runnable runnable) {
        return registerJob(env, name, schedule, runnable, null);
    }

    /**
     * Create a new {@link MonitoredJob}, set up the {@link MonitoredJobHealthCheck} and schedule the job on the given
     * {@link Environment}, with the given name, schedule, runnable and decision function.
     *
     * @param env        the Dropwizard environment to register the health check and schedule the job
     * @param name       the name of the job
     * @param schedule   the schedule for the job
     * @param runnable   the task that will be run inside the Monitored Job
     * @param decisionFn the function that will decide if the job should run
     * @return the new {@link MonitoredJob}
     * @throws IllegalArgumentException if a job with {@code name} has already been registered
     */
    public static MonitoredJob registerJob(Environment env,
                                           String name,
                                           JobSchedule schedule,
                                           Runnable runnable,
                                           Predicate<MonitoredJob> decisionFn) {
        var executor = newScheduledExecutor(env, name);
        return registerJob(env, name, schedule, runnable, decisionFn, executor);
    }

    /**
     * Create a new {@link MonitoredJob}, set up the {@link MonitoredJobHealthCheck} and schedule the job on the given
     * {@link Environment}, with the given name, schedule, runnable, decision function and
     * {@link ScheduledExecutorService}.
     *
     * @param env        the Dropwizard environment to register the health check and schedule the job
     * @param name       the name of the job
     * @param schedule   the schedule for the job
     * @param runnable   the task that will be run inside the Monitored Job
     * @param decisionFn the function that will decide if the job should run
     * @param executor   the scheduled executor to use to schedule the job
     * @return the new {@link MonitoredJob}
     * @throws IllegalArgumentException if a job with {@code name} has already been registered
     */
    public static MonitoredJob registerJob(Environment env,
                                           String name,
                                           JobSchedule schedule,
                                           Runnable runnable,
                                           Predicate<MonitoredJob> decisionFn,
                                           ScheduledExecutorService executor) {

        var job = MonitoredJob.builder()
                .name(name)
                .task(runnable)
                .decisionFunction(decisionFn)
                .build();

        return registerJob(env, job, schedule, executor);
    }

    /**
     * Using the given {@link MonitoredJob}, set up the {@link MonitoredJobHealthCheck} and schedule the job on the given
     * {@link Environment}, using the given schedule.
     *
     * @param env      the Dropwizard environment to register the health check and schedule the job
     * @param job      a {@link MonitoredJob} to schedule and monitor
     * @param schedule the schedule for the job
     * @return the given {@link MonitoredJob}
     * @throws IllegalArgumentException if a job with {@code name} has already been registered
     */
    public static MonitoredJob registerJob(Environment env, MonitoredJob job, JobSchedule schedule) {
        var executor = newScheduledExecutor(env, job.getName());
        return registerJob(env, job, schedule, executor);
    }

    /**
     * Using a given {@link MonitoredJob}, set up the {@link MonitoredJobHealthCheck} and schedule the job on the given
     * {@link Environment}, with the job's name, the given schedule and the given job.
     *
     * @param env      the Dropwizard environment to register the health check and schedule the job
     * @param job      a {@link MonitoredJob} to schedule and monitor
     * @param schedule the schedule for the job
     * @param executor the scheduled executor to use to schedule the job
     * @return the given {@link MonitoredJob}
     * @throws IllegalArgumentException if a job with {@code name} has already been registered
     */
    public static MonitoredJob registerJob(Environment env,
                                           MonitoredJob job,
                                           JobSchedule schedule,
                                           ScheduledExecutorService executor) {

        checkArgumentNotNull(env, "Dropwizard Environment must not be null");
        checkArgumentNotNull(job, "MonitoredJob must not be null");
        checkArgumentNotNull(schedule, "JobSchedule must not be null");
        checkArgumentNotNull(executor, "ScheduledExecutorService must not be null");

        var jobName = job.getName();

        validateJob(jobName, schedule);

        registerHealthCheck(env, jobName, schedule, job, null, null, null);
        scheduleJob(executor, schedule, job);

        JOBS.add(jobName);
        return job;
    }

    /**
     * Validates that the job name has not already been registered, and that the given
     * {@link JobSchedule} has a non-null, non-negative initial delay and a positive interval delay.
     *
     * @throws IllegalArgumentException if any validation check fails
     */
    private static void validateJob(String name, JobSchedule schedule) {
        checkArgument(!JOBS.contains(name), IllegalArgumentException.class,
                "Jobs cannot be registered more than once with the same name: {}", name);

        checkArgumentNotNull(schedule.getInitialDelay(),
                "Job '{}' must specify a non-null initial delay", name);

        checkArgument(schedule.getInitialDelay().toNanoseconds() >= 0,
                "Job '%s' must specify a non-negative initial delay", name);

        checkArgumentNotNull(schedule.getIntervalDelay(),
                "Job '{}' must specify a non-null interval delay", name);

        checkArgument(schedule.getIntervalDelay().toNanoseconds() > 0,
                "Job '%s' must specify a positive interval delay", name);
    }

    /**
     * Creates and registers a {@link MonitoredJobHealthCheck} for the given job in the Dropwizard
     * {@link Environment}. The {@code errorWarningDuration}, {@code thresholdFactor}, and
     * {@code suppressWarningThreshold} parameters are optional; when {@code null}, the corresponding
     * defaults defined in {@link MonitoredJobHealthCheck} are used.
     */
    private static void registerHealthCheck(Environment env,
                                            String name,
                                            JobSchedule schedule,
                                            MonitoredJob job,
                                            io.dropwizard.util.@Nullable Duration errorWarningDuration,
                                            @Nullable Double thresholdFactor,
                                            @Nullable Supplier<Boolean> suppressWarningThreshold) {

        LOG.debug("Creating and registering health check for job: {}", name);

        var healthCheck = MonitoredJobHealthCheck.builder()
                .job(job)
                .expectedFrequency(schedule.getIntervalDelay())
                .errorWarningDuration(errorWarningDuration)
                .thresholdFactor(thresholdFactor)
                .suppressWarningThreshold(suppressWarningThreshold)
                .build();

        env.healthChecks().register(f("Job: {}", name), healthCheck);
    }

    /**
     * Schedules the given {@link MonitoredJob} on the provided {@link ScheduledExecutorService}
     * using the initial delay and interval delay from the given {@link JobSchedule}.
     */
    private static void scheduleJob(ScheduledExecutorService executor, JobSchedule schedule, MonitoredJob job) {
        LOG.debug("Scheduling job: {} to start after initial delay: {} and run every: {}",
                job.getName(), schedule.getInitialDelay(), schedule.getIntervalDelay());

        executor.scheduleWithFixedDelay(
                job,
                schedule.getInitialDelay().toNanoseconds(),
                schedule.getIntervalDelay().toNanoseconds(),
                TimeUnit.NANOSECONDS);
    }

    /**
     * Clears all registered job names.
     *
     * @return the (sorted) job names that were registered before clearing them
     * @implNote Internally, this class maintains a static {@code Set} that contains the job
     * names that were registered via the {@code registerJob} methods to prevent jobs with
     * the same name being registered. This method provides a way to clear out the internal
     * job name {@code Set}, and is intended mainly for use in tests that may call one or
     * more of the {@code registerJob} methods multiple times across individual tests. This
     * is also why it is annotated with {@link VisibleForTesting} to make it clear that it
     * is not intended for usage in production code.
     */
    @VisibleForTesting
    public static SortedSet<String> clearRegisteredJobNames() {
        var names = registeredJobNames();
        JOBS.clear();
        return names;
    }

    /**
     * Provides the ability to get the job names that have been registered via the {@code registerJob} methods.
     * <p>
     * The returned job names are sorted.
     *
     * @return an unmodifiable set containing the registered job names
     */
    public static SortedSet<String> registeredJobNames() {
        return Collections.unmodifiableSortedSet(new TreeSet<>(JOBS));
    }

    /**
     * Returns a new {@link Builder} for creating and registering a {@link MonitoredJob} along with
     * its associated {@link MonitoredJobHealthCheck}.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for creating and registering {@link MonitoredJob} instances along with their associated
     * {@link MonitoredJobHealthCheck}.
     * <p>
     * Required parameters: {@code task}, {@code name}, {@code environment}, and {@code schedule}.
     * All other parameters are optional and will use defaults if not provided.
     * <p>
     * Call {@link #registerJob()} to complete registration, which creates the {@link MonitoredJob},
     * registers the health check, and schedules the job for execution.
     */
    public static class Builder {

        private Runnable task;
        private JobErrorHandler errorHandler;
        private Duration timeout;
        private String name;
        private Predicate<MonitoredJob> decisionFunction;
        private KiwiEnvironment kiwiEnvironment;
        private Environment environment;
        private JobSchedule schedule;
        private ScheduledExecutorService executor;
        private io.dropwizard.util.Duration errorWarningDuration;
        private Double thresholdFactor;
        private Supplier<Boolean> suppressWarningThreshold;

        /**
         * Sets the task (runnable) that will be executed by the {@link MonitoredJob}.
         *
         * @param task the task to run
         * @return this builder
         */
        public Builder task(Runnable task) {
            this.task = task;
            return this;
        }

        /**
         * Sets an optional {@link JobErrorHandler} to handle errors that occur during
         * job execution. If not provided, errors are handled by the default error 
         * handling behavior of {@link MonitoredJob}.
         *
         * @param errorHandler the error handler to use
         * @return this builder
         */
        public Builder errorHandler(JobErrorHandler errorHandler) {
            this.errorHandler = errorHandler;
            return this;
        }

        /**
         * Sets the timeout for the job execution as a Dropwizard
         * {@link io.dropwizard.util.Duration}.
         * <p>
         * This is a convenience overload that converts to a Java
         * {@link Duration} and calls {@link #timeout(Duration)}.
         *
         * @param timeout the timeout duration
         * @return this builder
         */
        public Builder timeout(io.dropwizard.util.Duration timeout) {
            return timeout(timeout.toJavaDuration());
        }

        /**
         * Sets the timeout for the job execution as a {@link Duration}.
         *
         * @param timeout the timeout duration
         * @return this builder
         */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * Sets the name of the job. Must be non-blank and unique across all registered
         * jobs.
         *
         * @param name the job name
         * @return this builder
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Sets an optional decision function that determines whether the job should
         * execute on a given invocation. If not provided, the job always executes.
         *
         * @param decisionFunction the predicate to evaluate before each job execution
         * @return this builder
         */
        public Builder decisionFunction(Predicate<MonitoredJob> decisionFunction) {
            this.decisionFunction = decisionFunction;
            return this;
        }

        /**
         * Sets the {@link KiwiEnvironment} to use for time-related operations within
         * the {@link MonitoredJob}. If not provided, a {@link org.kiwiproject.base.DefaultEnvironment}
         * is used.
         *
         * @param kiwiEnvironment the environment to use
         * @return this builder
         * @apiNote This is intended primarily for testing, to allow control over time-related behavior.
         */
        public Builder kiwiEnvironment(KiwiEnvironment kiwiEnvironment) {
            this.kiwiEnvironment = kiwiEnvironment;
            return this;
        }

        /**
         * Sets the Dropwizard {@link Environment} used to register the health check and
         * schedule the job. Required.
         *
         * @param environment the Dropwizard environment
         * @return this builder
         */
        public Builder environment(Environment environment) {
            this.environment = environment;
            return this;
        }

        /**
         * Sets the {@link JobSchedule} that controls the initial delay and interval
         * between job executions. Required.
         *
         * @param schedule the job schedule
         * @return this builder
         */
        public Builder schedule(JobSchedule schedule) {
            this.schedule = schedule;
            return this;
        }

        /**
         * Sets the {@link ScheduledExecutorService} to use for scheduling the job. If
         * not provided, a new executor is created via the Dropwizard {@link Environment}.
         *
         * @param executor the executor to use
         * @return this builder
         */
        public Builder executor(ScheduledExecutorService executor) {
            this.executor = executor;
            return this;
        }

        /**
         * Sets the duration within which a recent job error will cause the
         * {@link MonitoredJobHealthCheck} to report unhealthy. If not provided,
         * the default of {@link MonitoredJobHealthCheck#DEFAULT_WARNING_DURATION}
         * is used.
         *
         * @param errorWarningDuration the error warning duration
         * @return this builder
         */
        public Builder errorWarningDuration(io.dropwizard.util.Duration errorWarningDuration) {
            this.errorWarningDuration = errorWarningDuration;
            return this;
        }

        /**
         * Sets the factor applied to the expected job frequency to determine the
         * warning threshold duration in the {@link MonitoredJobHealthCheck}. If not
         * provided, the default of {@link MonitoredJobHealthCheck#DEFAULT_THRESHOLD_FACTOR} 
         * is used.
         *
         * @param thresholdFactor the threshold factor
         * @return this builder
         */
        public Builder thresholdFactor(Double thresholdFactor) {
            this.thresholdFactor = thresholdFactor;
            return this;
        }

        /**
         * Sets an optional supplier that returns {@code true} when the warning
         * threshold in the {@link MonitoredJobHealthCheck} should be suppressed, 
         * causing the health check to report healthy even if the time since the
         * last successful execution exceeds the warning threshold. If not provided,
         * the warning threshold is never suppressed.
         *
         * @param suppressWarningThreshold the supplier to evaluate when the warning
         *                                 threshold is exceeded
         * @return this builder
         */
        public Builder suppressWarningThreshold(Supplier<Boolean> suppressWarningThreshold) {
            this.suppressWarningThreshold = suppressWarningThreshold;
            return this;
        }

        /**
         * This is the terminal operation that builds a new {@link MonitoredJob} instance and registers it.
         * <p>
         * This method requires that the task, name, environment, and schedule have all been provided and otherwise
         * throws {@link IllegalArgumentException}. All other properties are optional and will have a default
         * supplied if necessary.
         *
         * @return the new job instance
         * @throws IllegalArgumentException if the task, name, environment, or schedule has not been specified
         */
        public MonitoredJob registerJob() {
            checkArgumentNotNull(task, "task is required");
            checkArgumentNotBlank(name, "non-blank name is required");
            checkArgumentNotNull(environment, "environment is required");
            checkArgumentNotNull(schedule, "schedule is required");

            var job = MonitoredJob.builder()
                    .task(task)
                    .errorHandler(errorHandler)
                    .timeout(timeout)
                    .name(name)
                    .decisionFunction(decisionFunction)
                    .environment(kiwiEnvironment)
                    .build();
            
            var localExecutor = isNull(this.executor) ? newScheduledExecutor(environment, name) : this.executor;

            validateJob(name, schedule);
            registerHealthCheck(environment, name, schedule, job, errorWarningDuration, thresholdFactor, suppressWarningThreshold);
            scheduleJob(localExecutor, schedule, job);

            JOBS.add(name);
            return job;
        }
    }

}
