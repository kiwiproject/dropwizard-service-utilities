package org.kiwiproject.dropwizard.util.job;

import static com.google.common.collect.Maps.newConcurrentMap;
import static java.util.Objects.isNull;
import static org.kiwiproject.base.KiwiPreconditions.checkArgument;
import static org.kiwiproject.base.KiwiPreconditions.checkArgumentNotNull;
import static org.kiwiproject.base.KiwiStrings.f;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.Pattern;

import com.google.common.annotations.VisibleForTesting;
import io.dropwizard.setup.Environment;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.kiwiproject.dropwizard.util.config.JobSchedule;
import org.kiwiproject.dropwizard.util.health.MonitoredJobHealthCheck;

/**
 * A set of utilities to assist in setting up MonitoredJobs with health checks.
 */
@Slf4j
@UtilityClass
public class MonitoredJobs {

    @VisibleForTesting
    static final Map<String, JobContext> JOBS = newConcurrentMap();

    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s");
    private static final String EMPTY_STRING = "";

    /**
     * Create a new {@link MonitoredJob}, setup the {@link MonitoredJobHealthCheck} and schedule the job on the given
     * {@link Environment}, with the given name, schedule and runnable.
     *
     * @param env       the Dropwizard environment to register the health check and schedule the job.
     * @param name      the name of the job
     * @param schedule  the schedule for the job
     * @param runnable  the task that will be run inside the Monitored Job
     * @return the new {@link MonitoredJob}
     */
    public static MonitoredJob registerJob(Environment env, String name, JobSchedule schedule, Runnable runnable) {
        return registerJob(env, name, schedule, runnable, null);
    }

    /**
     * Create a new {@link MonitoredJob}, setup the {@link MonitoredJobHealthCheck} and schedule the job on the given
     * {@link Environment}, with the given name, schedule, runnable and decision function.
     *
     * @param env           the Dropwizard environment to register the health check and schedule the job.
     * @param name          the name of the job
     * @param schedule      the schedule for the job
     * @param runnable      the task that will be run inside the Monitored Job
     * @param decisionFn    the function that will decide of the job should run
     * @return the new {@link MonitoredJob}
     */
    public static MonitoredJob registerJob(Environment env,
                                           String name,
                                           JobSchedule schedule,
                                           Runnable runnable,
                                           Function<MonitoredJob, Boolean> decisionFn) {
        var executor = buildScheduledExecutor(env, name);
        return registerJob(env, name, schedule, runnable, decisionFn, executor);
    }

    private static ScheduledExecutorService buildScheduledExecutor(Environment env, String name) {
        var safeName = f("Scheduled-{}-%d", WHITESPACE_PATTERN.matcher(name).replaceAll(EMPTY_STRING));
        return env.lifecycle()
                .scheduledExecutorService(safeName, true)
                .build();
    }

    /**
     * Create a new {@link MonitoredJob}, setup the {@link MonitoredJobHealthCheck} and schedule the job on the given
     * {@link Environment}, with the given name, schedule, runnable, decision function and
     * {@link ScheduledExecutorService}.
     *
     * @param env           the Dropwizard environment to register the health check and schedule the job.
     * @param name          the name of the job
     * @param schedule      the schedule for the job
     * @param runnable      the task that will be run inside the Monitored Job
     * @param decisionFn    the function that will decide of the job should run
     * @param executor      the scheduled executor to use to schedule the job
     * @return the new {@link MonitoredJob}
     */
    public static MonitoredJob registerJob(Environment env,
                                           String name,
                                           JobSchedule schedule,
                                           Runnable runnable,
                                           Function<MonitoredJob, Boolean> decisionFn,
                                           ScheduledExecutorService executor) {
        validateJob(name, schedule);

        var job = MonitoredJob.builder()
                .name(name)
                .task(runnable)
                .decisionFunction(decisionFn)
                .build();

        var healthCheck = registerHealthCheck(env, name, schedule, job);

        scheduleJob(executor, schedule, job);

        JOBS.put(name, new JobContext(job, healthCheck, executor));
        return job;
    }

    private static void validateJob(String name, JobSchedule schedule) {
        checkArgument(isNull(JOBS.get(name)), IllegalArgumentException.class,
                "Jobs cannot be registered more than once with the same name: {}", name);

        checkArgumentNotNull(schedule.getInitialDelay(),
                "Job '{}' must specify a non-null initial delay", name);

        checkArgumentNotNull(schedule.getIntervalDelay(),
                "Job '{}' must specify a non-null interval delay", name);
    }

    private static MonitoredJobHealthCheck registerHealthCheck(Environment env, String name,
                                                               JobSchedule schedule, MonitoredJob job) {

        LOG.debug("Creating and registering health check for job: {}", name);

        var healthCheck = MonitoredJobHealthCheck.builder()
                .job(job)
                .expectedFrequency(schedule.getIntervalDelay())
                .build();

        env.healthChecks().register(f("Job: {}", name), healthCheck);
        return healthCheck;
    }

    private static void scheduleJob(ScheduledExecutorService executor, JobSchedule schedule, MonitoredJob job) {
        LOG.debug("Scheduling job: {} to run every: {}", job.getName(), schedule.getIntervalDelay());
        executor.scheduleWithFixedDelay(job, schedule.getInitialDelay().toSeconds(),
                schedule.getIntervalDelay().toSeconds(), TimeUnit.SECONDS);
    }

    /**
     * Container that holds the newly created {@link MonitoredJob}, {@link MonitoredJobHealthCheck}
     * and the {@link Executor} running the job.
     */
    @Getter
    @AllArgsConstructor
    public static class JobContext {
        private final MonitoredJob job;
        private final MonitoredJobHealthCheck healthCheck;
        private final Executor executor;
    }
}
