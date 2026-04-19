package org.kiwiproject.dropwizard.util.task;

import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.kiwiproject.base.KiwiPreconditions.checkArgumentNotBlank;
import static org.kiwiproject.base.KiwiPreconditions.checkArgumentNotNull;
import static org.kiwiproject.base.KiwiPreconditions.requireNotBlank;
import static org.kiwiproject.base.KiwiPreconditions.requireNotNull;
import static org.kiwiproject.base.KiwiStrings.f;
import static org.kiwiproject.base.UUIDs.randomUUIDString;
import static org.kiwiproject.collect.KiwiLists.first;
import static org.kiwiproject.time.KiwiDurationFormatters.formatNanosecondDurationWords;

import com.google.common.annotations.VisibleForTesting;
import io.dropwizard.servlets.tasks.Task;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.text.CaseUtils;
import org.jspecify.annotations.Nullable;
import org.kiwiproject.beta.time.Timing;
import org.kiwiproject.collect.KiwiMaps;
import org.kiwiproject.concurrent.Async;

import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * A task that executes a job ({@link Runnable}) on demand, synchronously or asynchronously.
 * <p>
 * By default, the job runs asynchronously. Pass a {@code sync} query parameter with a truthy
 * value (case-insensitive {@code true}, {@code on}, {@code y}, {@code t}, or {@code yes})
 * to force synchronous execution.
 * <p>
 * Only one execution is permitted at a time. If the task is invoked while already running,
 * the request returns immediately with a message in the task output; the job is not executed.
 * <p>
 * The {@link ExecuteJobTaskConfig#canRun()} supplier is consulted before each execution. If
 * it returns {@code false}, execution is skipped. In that case, if the
 * {@link ExecuteJobTaskConfig#cannotRunExceptionProvider()} returns a non-null exception,
 * that exception is thrown; otherwise a message is written to the task output.
 * <p>
 * When running asynchronously, an {@code asyncId} is generated and included in both the task
 * output and all log messages for that execution, providing a correlation handle between the
 * HTTP response and the application logs.
 * <p>
 * If {@code taskName} is not provided, the Dropwizard task name defaults to the camelCase form
 * of {@code jobName} (e.g., "Data Sync Job" becomes "dataSyncJob"), and the task is accessible
 * at {@code POST /tasks/dataSyncJob}.
 * <p>
 * Requires an {@link ExecuteJobTaskConfig}.
 *
 * @implNote Truthy value parsing is delegated to {@code BooleanUtils.toBoolean(String)} from
 * Apache Commons Lang. The five values listed above are the documented contract; if the
 * library's behavior changes, the implementation should be updated to preserve them.
 */
@Slf4j
public class ExecuteJobTask extends Task {

    private final Runnable job;
    private final String jobName;
    private final BooleanSupplier canRun;
    private final Supplier<@Nullable Exception> cannotRunExceptionProvider;
    private final ExecutorService executorService;

    @VisibleForTesting
    final AtomicBoolean running;

    /**
     * Create a new instance using the builder.
     *
     * @param job        the job to execute; required
     * @param jobName    a human-readable name for the job, used in log and output messages; required
     * @param taskName   the Dropwizard task name used in the {@code POST /tasks/{taskName}} URL;
     *                   if blank, defaults to the camelCase form of {@code jobName}
     * @param taskConfig configuration controlling execution behavior; required
     */
    @Builder
    public ExecuteJobTask(Runnable job,
                          String jobName,
                          String taskName,
                          ExecuteJobTaskConfig taskConfig) {

        super(taskNameOrDefault(taskName, jobName));

        this.job = requireNotNull(job, "job must not be null");
        this.jobName = requireNotBlank(jobName, "jobName must not be blank");

        checkArgumentNotNull(taskConfig, "taskConfig must not be null");
        this.canRun = taskConfig.canRun();
        this.cannotRunExceptionProvider = taskConfig.cannotRunExceptionProvider();
        this.executorService = taskConfig.executorService();

        this.running = new AtomicBoolean(false);
    }

    @VisibleForTesting
    static String taskNameOrDefault(@Nullable String taskName, String jobName) {
        if (isNotBlank(taskName)) {
            return taskName;
        }

        checkArgumentNotBlank(jobName, "jobName must not be blank");
        return CaseUtils.toCamelCase(jobName, false);
    }

    @Override
    public void execute(Map<String, List<String>> parameters, PrintWriter output) throws Exception {
        if (!running.compareAndSet(false, true)) {
            var message = f("Task for {} is already running. Skipping.", jobName);
            LOG.warn(message);
            output.println(message);
            return;
        }

        try {
            if (!canRun.getAsBoolean()) {
                running.set(false);

                var ex = cannotRunExceptionProvider.get();
                if (nonNull(ex)) {
                    throw ex;
                }

                var message = f("Not executing {} (canRun returned false)", jobName);
                LOG.info(message);
                output.println(message);
                return;
            }

            var sync = isSync(parameters);

            if (sync) {
                runJobSync(output);
            } else {
                runJobAsync(output);
            }
        } catch (Exception e) {
            running.set(false);
            throw e;
        }
    }

    private boolean isSync(Map<String, List<String>> parameters) {
        var syncParamValues = KiwiMaps.getTypedList(parameters, "sync", String.class, List.of("false"));

        //noinspection DataFlowIssue - because we are providing a non-null default value, it won't be null
        return BooleanUtils.toBoolean(first(syncParamValues));
    }

    @SuppressWarnings("UnstableApiUsage")
    private void runJobSync(PrintWriter output) {
        try {
            LOG.info("Executing {} synchronously", jobName);

            var result = Timing.timeNoResult(job);
            var durationInWords = formatNanosecondDurationWords(result.getElapsedNanos());

            if (result.hasException()) {
                var ex = result.getException().orElseThrow();
                LOG.error("Failed executing {} with exception in {}", jobName, durationInWords, ex);
                throw ex;
            }

            output.println(f("Successfully executed {} in {}", jobName, durationInWords));
        } finally {
            running.set(false);
        }
    }

    @SuppressWarnings("UnstableApiUsage")
    private void runJobAsync(PrintWriter output) {
        var asyncId = randomUUIDString();
        LOG.info("Executing {} asynchronously with asyncId {}", jobName, asyncId);

        try {
            Async.runAsync(() -> {
                try {
                    var result = Timing.timeNoResult(job);
                    var durationInWords = formatNanosecondDurationWords(result.getElapsedNanos());

                    if (result.hasException()) {
                        var ex = result.getException().orElseThrow();
                        LOG.error("Failed executing {} with exception in {} (asyncId: {})",
                                jobName, durationInWords, asyncId, ex);
                    } else {
                        LOG.info("Successfully executed {} in {} (asyncId: {})",
                                jobName, durationInWords, asyncId);
                    }
                } finally {
                    running.set(false);
                }
            }, executorService);
        } catch (RuntimeException e) {  // handle runtime exceptions, e.g., a RejectedExecutionException
            LOG.error("Failed to submit async execution of {}", jobName, e);
            running.set(false);
            throw e;
        }

        output.println(f("Started executing {} in background (asyncId: {}). Logs will contain results after completion.",
                jobName, asyncId));
    }
}
