package org.kiwiproject.dropwizard.util.job;

import static org.kiwiproject.base.KiwiPreconditions.checkArgument;
import static org.kiwiproject.base.KiwiPreconditions.checkArgumentNotNull;
import static org.kiwiproject.base.KiwiStrings.f;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.Pattern;

import com.google.common.annotations.VisibleForTesting;
import io.dropwizard.setup.Environment;
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
    static final Set<String> JOBS = new HashSet<>();

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

        registerHealthCheck(env, name, schedule, job);
        scheduleJob(executor, schedule, job);

        JOBS.add(name);
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

    private static void registerHealthCheck(Environment env, String name,
                                                               JobSchedule schedule, MonitoredJob job) {

        LOG.debug("Creating and registering health check for job: {}", name);

        var healthCheck = MonitoredJobHealthCheck.builder()
                .job(job)
                .expectedFrequency(schedule.getIntervalDelay())
                .build();

        env.healthChecks().register(f("Job: {}", name), healthCheck);
    }

    private static void scheduleJob(ScheduledExecutorService executor, JobSchedule schedule, MonitoredJob job) {
        LOG.debug("Scheduling job: {} to run every: {}", job.getName(), schedule.getIntervalDelay());
        executor.scheduleWithFixedDelay(job, schedule.getInitialDelay().toSeconds(),
                schedule.getIntervalDelay().toSeconds(), TimeUnit.SECONDS);
    }
}
