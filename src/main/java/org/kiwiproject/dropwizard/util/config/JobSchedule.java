package org.kiwiproject.dropwizard.util.config;

import io.dropwizard.util.Duration;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.NotNull;

/**
 * Config object that defines a schedule for background jobs.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class JobSchedule {

    @NotNull
    private Duration initialDelay = Duration.seconds(30);

    @NotNull
    private Duration intervalDelay;

}
