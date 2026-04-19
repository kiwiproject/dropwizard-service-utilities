package org.kiwiproject.dropwizard.util.task;

import static java.util.Objects.isNull;
import static org.kiwiproject.base.KiwiPreconditions.requireNotNull;

import lombok.Builder;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.ExecutorService;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Configuration for {@link ExecuteJobTask}.
 */
@Getter
@Accessors(fluent = true)
@SuppressWarnings("ClassCanBeRecord")
public class ExecuteJobTaskConfig {

    private final BooleanSupplier canRun;
    private final Supplier<@Nullable Exception> cannotRunExceptionProvider;
    private final ExecutorService executorService;

    /**
     * Create a new instance.
     *
     * @param canRun                     provides a boolean that determines whether the task is allowed to run;
     *                                   if not given, the default value is true
     * @param cannotRunExceptionProvider a Supplier that can optionally provide an exception to be thrown if
     *                                   the task is not allowed to run; if not given, the default value
     *                                   always returns null, meaning no exception would be thrown
     * @param executorService            a required {@link ExecutorService} used for async task execution;
     *                                   this should be externally managed, e.g., building one using
     *                                   {@link io.dropwizard.lifecycle.setup.LifecycleEnvironment#executorService(String)}
     */
    @Builder
    ExecuteJobTaskConfig(@Nullable BooleanSupplier canRun,
                         @Nullable Supplier<@Nullable Exception> cannotRunExceptionProvider,
                         ExecutorService executorService) {
        this.canRun = isNull(canRun) ? () -> true : canRun;
        this.cannotRunExceptionProvider = isNull(cannotRunExceptionProvider) ? () -> null : cannotRunExceptionProvider;
        this.executorService = requireNotNull(executorService);
    }

    /**
     * Factory to create a configuration that is always allowed to run.
     *
     * @param executorService a required {@link ExecutorService} used for async task execution;
     *                        this should be externally managed
     * @return a new instance
     */
    public static ExecuteJobTaskConfig canAlwaysRun(ExecutorService executorService) {
        return ExecuteJobTaskConfig.builder()
                .canRun(() -> true)
                .executorService(executorService)
                .build();
    }

    /**
     * Factory to create a configuration with a custom condition controlling whether the task
     * is allowed to run. If the condition returns {@code false}, a message is written to the
     * task output and execution is skipped without throwing an exception.
     * <p>
     * A common use case is leader election: pass a supplier that checks whether this service
     * instance holds the leader latch, so the job only executes on the elected leader.
     *
     * @param canRun          a supplier that determines whether the task is allowed to run
     * @param executorService a required {@link ExecutorService} used for async task execution;
     *                        this should be externally managed
     * @return a new instance
     */
    public static ExecuteJobTaskConfig canRunWhen(BooleanSupplier canRun, ExecutorService executorService) {
        requireNotNull(canRun, "canRun must not be null");
        return ExecuteJobTaskConfig.builder()
                .canRun(canRun)
                .executorService(executorService)
                .build();
    }
}
