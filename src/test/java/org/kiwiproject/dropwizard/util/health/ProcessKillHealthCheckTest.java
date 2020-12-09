package org.kiwiproject.dropwizard.util.health;

import static org.kiwiproject.test.assertj.dropwizard.metrics.HealthCheckResultAssertions.assertThatHealthCheck;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.kiwiproject.base.process.KillSignal;
import org.kiwiproject.base.process.KillTimeoutAction;
import org.kiwiproject.base.process.ProcessHelper;

import java.io.IOException;
import java.io.UncheckedIOException;

@DisplayName("ProcessKillHealthCheck")
class ProcessKillHealthCheckTest {

    private ProcessKillHealthCheck healthCheck;
    private ProcessHelper processes;
    private long processId;

    @BeforeEach
    void setUp() {
        var process = mock(Process.class);
        processId = 42;
        processes = mock(ProcessHelper.class);

        when(processes.launch("sleep", "10")).thenReturn(process);
        when(process.pid()).thenReturn(processId);

        healthCheck = new ProcessKillHealthCheck(processes);
    }

    @Nested
    class IsHealthy {

        @Test
        void whenProcessKilledSuccessfully() {
            when(processes.kill(processId, KillSignal.SIGHUP, KillTimeoutAction.FORCE_KILL)).thenReturn(0);
            assertThatHealthCheck(healthCheck)
                    .isHealthy();
        }
    }

    @Nested
    class IsUnhealthy {

        @Test
        void whenExceptionIsThrown() {
            var uncheckedIOException = new UncheckedIOException(new IOException("I/0 error"));
            when(processes.kill(processId, KillSignal.SIGHUP, KillTimeoutAction.FORCE_KILL))
                    .thenThrow(uncheckedIOException);

            assertThatHealthCheck(healthCheck)
                    .isUnhealthy()
                    .hasMessageStartingWith("Failed to kill 'sleep' process: ")
                    .hasMessageEndingWith(uncheckedIOException.getMessage());
        }

        @Test
        void whenUnsuccessfulKillExitCode() {
            when(processes.kill(processId, KillSignal.SIGHUP, KillTimeoutAction.FORCE_KILL)).thenReturn(137);
            assertThatHealthCheck(healthCheck)
                    .isUnhealthy()
                    .hasMessage("Unsuccessful kill exit code: 137");
        }
    }
}
