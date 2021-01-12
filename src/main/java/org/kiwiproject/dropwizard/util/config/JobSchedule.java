package org.kiwiproject.dropwizard.util.config;

import io.dropwizard.util.Duration;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.NotNull;

/**
 * Config object that defines a schedule for background jobs.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public class JobSchedule {

    /**
     * Initial delay before the job will run. This should be passed to the ScheduledExecutor's initialDelay. Defaults
     * to 30 seconds.
     */
    @NotNull
    @Builder.Default
    private Duration initialDelay = Duration.seconds(30);

    /**
     * Delay between job runs.  If using a fixedDelay, this will be the time between the end of one job run and the
     * start of the next job run. If using a fixedRate, this will be the time between the start of one job and the start
     * of the next run.
     */
    @NotNull
    private Duration intervalDelay;

}
