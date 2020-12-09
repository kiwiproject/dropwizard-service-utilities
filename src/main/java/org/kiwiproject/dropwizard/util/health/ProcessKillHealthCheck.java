package org.kiwiproject.dropwizard.util.health;

import static org.kiwiproject.metrics.health.HealthCheckResults.newHealthyResult;
import static org.kiwiproject.metrics.health.HealthCheckResults.newUnhealthyResult;

import com.codahale.metrics.health.HealthCheck;
import lombok.extern.slf4j.Slf4j;
import org.kiwiproject.base.process.KillSignal;
import org.kiwiproject.base.process.KillTimeoutAction;
import org.kiwiproject.base.process.ProcessHelper;

/**
 * Health check that checks if we are able to kill processes. This check attempts to kill a simple sleep command that
 * it launches.
 */
@Slf4j
public class ProcessKillHealthCheck extends HealthCheck {

    private final ProcessHelper processes;

    public ProcessKillHealthCheck(ProcessHelper processes) {
        this.processes = processes;
    }

    @Override
    protected Result check() {
        // Launch process outside try/catch. We are not testing process launching here, so if error occurs just
        // let that exception propagate and Dropwizard can handle it.
        var process = processes.launch("sleep", "10");

        try {
            return resultBasedOnSuccessfulKill(process);
        } catch (Exception e) {
            LOG.trace("Process kill check is not healthy", e);
            return newUnhealthyResult("Failed to kill 'sleep' process: " + e.getMessage());
        }
    }

    private Result resultBasedOnSuccessfulKill(Process process) {
        var exitCode = processes.kill(process.pid(), KillSignal.SIGTERM, KillTimeoutAction.FORCE_KILL);
        return resultBasedOnExitCode(exitCode);
    }

    private static Result resultBasedOnExitCode(int exitCode) {
        if (exitCode == 0) {
            return newHealthyResult();
        }

        return newUnhealthyResult("Unsuccessful kill exit code: " + exitCode);
    }
}
