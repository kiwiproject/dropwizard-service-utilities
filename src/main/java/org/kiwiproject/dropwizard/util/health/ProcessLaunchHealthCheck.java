package org.kiwiproject.dropwizard.util.health;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.kiwiproject.metrics.health.HealthCheckResults.newHealthyResult;
import static org.kiwiproject.metrics.health.HealthCheckResults.newUnhealthyResult;

import com.codahale.metrics.health.HealthCheck;
import lombok.extern.slf4j.Slf4j;
import org.kiwiproject.base.process.ProcessHelper;
import org.kiwiproject.io.KiwiIO;

/**
 * Health check that checks if we are able to launch processes. This check attempts to launch a simple echo process
 * and verifies the output is as expected.
 */
@Slf4j
public class ProcessLaunchHealthCheck extends HealthCheck {

    static final String ECHO_MESSAGE = "Launch process health check";

    private final ProcessHelper processes;

    public ProcessLaunchHealthCheck(ProcessHelper processes) {
        this.processes = processes;
    }

    @Override
    protected Result check() {
        try {
            var process = processes.launch("echo", ECHO_MESSAGE);
            var line = KiwiIO.readInputStreamOf(process, UTF_8);
            return resultBasedOnEchoOutput(line);
        } catch (Exception e) {
            LOG.trace("Process launch health check is not healthy", e);
            return newUnhealthyResult("Failed launching an 'echo' process: " + e.getMessage());
        }
    }

    private static Result resultBasedOnEchoOutput(String line) {
        if (ECHO_MESSAGE.equals(line.stripTrailing())) {
            return newHealthyResult();
        }

        return newUnhealthyResult("Output [%s] from 'echo' does not match expected [%s]", line, ECHO_MESSAGE);
    }
}
