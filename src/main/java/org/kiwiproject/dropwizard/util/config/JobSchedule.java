package org.kiwiproject.dropwizard.util.config;

import static org.kiwiproject.base.KiwiPreconditions.checkArgumentNotNull;

import io.dropwizard.util.Duration;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Config object that defines a schedule for background jobs.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public class JobSchedule {

    static final Duration DEFAULT_INITIAL_DELAY = Duration.seconds(30);

    /**
     * Initial delay before the job will run. This should be passed to the ScheduledExecutor's initialDelay. Defaults
     * to 30 seconds.
     */
    @NotNull
    @Builder.Default
    private Duration initialDelay = DEFAULT_INITIAL_DELAY;

    /**
     * Delay between job runs.  If using a fixedDelay, this will be the time between the end of one job run and the
     * start of the next job run. If using a fixedRate, this will be the time between the start of one job and the start
     * of the next run.
     */
    @NotNull
    private Duration intervalDelay;

    /**
     * Create a new {@link JobSchedule} instance with the given interval delay and the default initial delay.
     * <p>
     * This is useful when programmatically creating JobSchedule objects.
     *
     * @param intervalDelay the interval delay as a Dropwizard {@link Duration}
     * @return a new instance
     */
    public static JobSchedule ofIntervalDelay(Duration intervalDelay) {
        checkIntervalDelayNotNull(intervalDelay);

        return JobSchedule.builder()
                .intervalDelay(intervalDelay)
                .build();
    }

    /**
     * Create a new {@link JobSchedule} instance with the given interval delay and the default initial delay.
     * <p>
     * This is useful when programmatically creating JobSchedule objects.
     *
     * @param intervalDelay the interval delay as a JDK {@link java.time.Duration}
     * @return a new instance
     */
    public static JobSchedule ofIntervalDelay(java.time.Duration intervalDelay) {
        checkIntervalDelayNotNull(intervalDelay);

        return JobSchedule.builder()
                .intervalDelay(Duration.milliseconds(intervalDelay.toMillis()))
                .build();
    }

    private static void checkIntervalDelayNotNull(Object intervalDelay) {
        checkArgumentNotNull(intervalDelay, "intervalDelay must not be null");
    }
}
