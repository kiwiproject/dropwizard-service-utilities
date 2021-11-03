package org.kiwiproject.dropwizard.util.job;

import static java.util.Objects.isNull;
import static org.kiwiproject.base.KiwiPreconditions.checkArgument;
import static org.kiwiproject.base.KiwiPreconditions.checkArgumentNotBlank;
import static org.kiwiproject.base.KiwiPreconditions.checkArgumentNotNull;
import static org.kiwiproject.base.KiwiStrings.f;
import static org.kiwiproject.dropwizard.util.lifecycle.StandardLifecycles.newScheduledExecutor;

import com.google.common.annotations.VisibleForTesting;
import io.dropwizard.setup.Environment;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.kiwiproject.base.KiwiEnvironment;
import org.kiwiproject.dropwizard.util.KiwiDropwizardDurations;
import org.kiwiproject.dropwizard.util.config.JobSchedule;
import org.kiwiproject.dropwizard.util.health.MonitoredJobHealthCheck;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * A set of utilities to assist in setting up MonitoredJobs with health checks.
 * <p>
 * There are convenience static factory methods to create and register {@link MonitoredJob} instances. If none of
 * them suits your requirements, use the {@link #builder()} for control over job creation and registration.
 */
@Slf4j
@UtilityClass
public class MonitoredJobs {

    @VisibleForTesting
    static final Set<String> JOBS = new HashSet<>();

    /**
     * Create a new {@link MonitoredJob}, setup the {@link MonitoredJobHealthCheck} and schedule the job on the given
     * {@link Environment}, with the given name, schedule and runnable.
     *
     * @param env      the Dropwizard environment to register the health check and schedule the job.
     * @param name     the name of the job
     * @param schedule the schedule for the job
     * @param runnable the task that will be run inside the Monitored Job
     * @return the new {@link MonitoredJob}
     */
    public static MonitoredJob registerJob(Environment env, String name, JobSchedule schedule, Runnable runnable) {
        return registerJob(env, name, schedule, runnable, null);
    }

    /**
     * Create a new {@link MonitoredJob}, setup the {@link MonitoredJobHealthCheck} and schedule the job on the given
     * {@link Environment}, with the given name, schedule, runnable and decision function.
     *
     * @param env        the Dropwizard environment to register the health check and schedule the job.
     * @param name       the name of the job
     * @param schedule   the schedule for the job
     * @param runnable   the task that will be run inside the Monitored Job
     * @param decisionFn the function that will decide of the job should run
     * @return the new {@link MonitoredJob}
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
     * Create a new {@link MonitoredJob}, setup the {@link MonitoredJobHealthCheck} and schedule the job on the given
     * {@link Environment}, with the given name, schedule, runnable, decision function and
     * {@link ScheduledExecutorService}.
     *
     * @param env        the Dropwizard environment to register the health check and schedule the job.
     * @param name       the name of the job
     * @param schedule   the schedule for the job
     * @param runnable   the task that will be run inside the Monitored Job
     * @param decisionFn the function that will decide of the job should run
     * @param executor   the scheduled executor to use to schedule the job
     * @return the new {@link MonitoredJob}
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
     * Using the given {@link MonitoredJob}, setup the {@link MonitoredJobHealthCheck} and schedule the job on the given
     * {@link Environment}, using the given schedule.
     *
     * @param env      the Dropwizard environment to register the health check and schedule the job
     * @param job      a {@link MonitoredJob} to schedule and monitor
     * @param schedule the schedule for the job
     * @return the given {@link MonitoredJob}
     */
    public static MonitoredJob registerJob(Environment env, MonitoredJob job, JobSchedule schedule) {
        var executor = newScheduledExecutor(env, job.getName());
        return registerJob(env, job, schedule, executor);
    }

    /**
     * Using a given {@link MonitoredJob}, setup the {@link MonitoredJobHealthCheck} and schedule the job on the given
     * {@link Environment}, with the job's name, the given schedule and the given job.
     *
     * @param env      the Dropwizard environment to register the health check and schedule the job.
     * @param job      a {@link MonitoredJob} to schedule and monitor.
     * @param schedule the schedule for the job.
     * @param executor the scheduled executor to use to schedule the job.
     * @return the given {@link MonitoredJob}.
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

        registerHealthCheck(env, jobName, schedule, job);
        scheduleJob(executor, schedule, job);

        JOBS.add(jobName);
        return job;
    }

    private static void validateJob(String name, JobSchedule schedule) {
        checkArgument(!JOBS.contains(name), IllegalArgumentException.class,
                "Jobs cannot be registered more than once with the same name: {}", name);

        checkArgumentNotNull(schedule.getInitialDelay(),
                "Job '{}' must specify a non-null initial delay", name);

        checkArgumentNotNull(schedule.getIntervalDelay(),
                "Job '{}' must specify a non-null interval delay", name);
    }

    private static void registerHealthCheck(Environment env,
                                            String name,
                                            JobSchedule schedule,
                                            MonitoredJob job) {

        LOG.debug("Creating and registering health check for job: {}", name);

        var healthCheck = MonitoredJobHealthCheck.builder()
                .job(job)
                .expectedFrequency(schedule.getIntervalDelay())
                .build();

        env.healthChecks().register(f("Job: {}", name), healthCheck);
    }

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
     * Builder to allow for customizing all aspects of a {@link MonitoredJob} and then registering it.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for creating and registering {@link MonitoredJob} instances.
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

        public Builder task(Runnable task) {
            this.task = task;
            return this;
        }

        public Builder errorHandler(JobErrorHandler errorHandler) {
            this.errorHandler = errorHandler;
            return this;
        }

        public Builder timeout(io.dropwizard.util.Duration timeout) {
            return timeout(KiwiDropwizardDurations.fromDropwizardDuration(timeout));
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder decisionFunction(Predicate<MonitoredJob> decisionFunction) {
            this.decisionFunction = decisionFunction;
            return this;
        }

        public Builder kiwiEnvironment(KiwiEnvironment kiwiEnvironment) {
            this.kiwiEnvironment = kiwiEnvironment;
            return this;
        }

        public Builder environment(Environment environment) {
            this.environment = environment;
            return this;
        }

        public Builder schedule(JobSchedule schedule) {
            this.schedule = schedule;
            return this;
        }

        public Builder executor(ScheduledExecutorService executor) {
            this.executor = executor;
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
         * @throws IllegalArgumentException if task, name, environment, or schedule has not been specified
         * @see #registerJob(Environment, MonitoredJob, JobSchedule, ScheduledExecutorService)
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

            return MonitoredJobs.registerJob(environment, job, schedule, localExecutor);
        }
    }

}
