package org.kiwiproject.dropwizard.util.health;

import static org.kiwiproject.metrics.health.HealthCheckResults.newHealthyResult;
import static org.kiwiproject.metrics.health.HealthCheckResults.newUnhealthyResult;

import com.codahale.metrics.health.HealthCheck;
import org.kiwiproject.base.process.ProcessHelper;

import java.util.List;

/**
 * A health check that checks if we are able to find processes using pgrep, specifically via
 * {@link ProcessHelper#pgrep(String)}. This check tries to pgrep any java processes. Since we must be inside a java
 * process, we should always find at least one.
 */
public class ProcessGrepHealthCheck extends HealthCheck {

    /**
     * A default name that can be used when registering this health check.
     */
    @SuppressWarnings("unused")
    public static final String DEFAULT_NAME = "Process Grep";

    private final ProcessHelper processes;

    public ProcessGrepHealthCheck(ProcessHelper processes) {
        this.processes = processes;
    }

    @Override
    protected Result check() {
        try {
            var javaProcesses = processes.pgrep("java");
            return resultBasedOnReturnedProcesses(javaProcesses);
        } catch (Exception e) {
            return newUnhealthyResult("Failed to find java processes: " + e);
        }
    }

    private static Result resultBasedOnReturnedProcesses(List<Long> javaProcesses) {
        if (javaProcesses.isEmpty()) {
            return newUnhealthyResult("No java processes found (even though this app is a java processes)");
        }

        return newHealthyResult();
    }
}
