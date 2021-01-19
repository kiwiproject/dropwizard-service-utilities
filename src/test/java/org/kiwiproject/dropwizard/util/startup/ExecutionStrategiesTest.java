package org.kiwiproject.dropwizard.util.startup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.kiwiproject.test.assertj.KiwiAssertJ.assertIsExactType;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.kiwiproject.base.process.Processes;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

@DisplayName("ExecutionStrategies")
@Slf4j
class ExecutionStrategiesTest {

    @Test
    void shouldBuildNoOpStrategy() {
        assertThat(ExecutionStrategies.noOp())
                .isExactlyInstanceOf(ExecutionStrategies.NoOpExecutionStrategy.class);
    }

    @Test
    void shouldBuildSystemExitStrategy() {
        var strategy = ExecutionStrategies.systemExit();
        var systemExitStrategy = assertIsExactType(strategy, ExecutionStrategies.SystemExitExecutionStrategy.class);
        assertThat(systemExitStrategy.getExitCode()).isEqualTo(1);
    }

    @Test
    void shouldBuildSystemExitStrategyWithExitCode() {
        var strategy = ExecutionStrategies.systemExit(42);
        var systemExitStrategy = assertIsExactType(strategy, ExecutionStrategies.SystemExitExecutionStrategy.class);
        assertThat(systemExitStrategy.getExitCode()).isEqualTo(42);
    }

    @Nested
    class NoOpStrategy {

        @Test
        void shouldDoNothing() {
            var executionStrategy = ExecutionStrategies.noOp();
            assertThatCode(executionStrategy::exit).doesNotThrowAnyException();
        }
    }

    @Nested
    class ExitFlaggingStrategy {

        @Test
        void shouldFlagCallsToExit() {
            var executionStrategy = ExecutionStrategies.exitFlagging();
            assertThatCode(executionStrategy::exit).doesNotThrowAnyException();

            var exitFlaggingStrategy =
                    assertIsExactType(executionStrategy, ExecutionStrategies.ExitFlaggingExecutionStrategy.class);
            assertThat(exitFlaggingStrategy.didExit()).isTrue();
        }

        @Test
        void shouldNotFlagWhenExitNotCalled() {
            var exitFlaggingStrategy =
                    assertIsExactType(ExecutionStrategies.exitFlagging(), ExecutionStrategies.ExitFlaggingExecutionStrategy.class);
            assertThat(exitFlaggingStrategy.didExit()).isFalse();
        }
    }

    @Nested
    class SystemExitStrategy {

        @ParameterizedTest
        @CsvSource({
                "1, 0",
                "143 , 0",
                "127, 25",
                "143, 50"
        })
        void shouldExitTheJVM(int exitCode, long exitWaitDelayMillis) throws IOException {
            var result = execTestApplication(exitCode, exitWaitDelayMillis);

            assertThat(result.pid).isPositive();
            assertThat(result.exitValue)
                    .describedAs("Expecting non-null exit code (null means timeout waiting for process to exit)")
                    .isEqualTo(exitCode);
        }
    }

    @Slf4j
    public static class TestApplication {
        public static void main(String[] args) {
            int exitCode = Integer.parseInt(args[0]);
            long exitWaitDelayMillis = Long.parseLong(args[1]);
            LOG.debug("Using exitCode: {} ; exitWaitDelayMillis: {}", exitCode, exitWaitDelayMillis);

            // pretend some unrecoverable error occurred...
            var executionStrategy = ExecutionStrategies.systemExit(exitCode);
            new SystemExecutioner(executionStrategy).exit(exitWaitDelayMillis, TimeUnit.MILLISECONDS);
        }
    }

    @SuppressWarnings("SameParameterValue")
    private static ExecResult execTestApplication(int exitCode, long exitWaitDelayMillis) throws IOException {
        var javaHome = System.getProperty("java.home");
        var javaBin = Path.of(javaHome, "bin", "java").toString();
        var classpath = System.getProperty("java.class.path");
        var className = TestApplication.class.getName();
        var command = List.of(javaBin,
                "-cp", classpath,
                className,
                String.valueOf(exitCode),
                String.valueOf(exitWaitDelayMillis));

        LOG.debug("Executing TestApplication using {} with exitCode {} and exitWaitDelayMillis {}",
                javaBin, exitCode, exitWaitDelayMillis);
        var process = new ProcessBuilder(command).start();
        var exitValueOrNull = Processes.waitForExit(process, 5, TimeUnit.SECONDS).orElse(null);
        var execResult = new ExecResult(process.pid(), exitValueOrNull);
        LOG.debug("Received result: {}", execResult);

        return execResult;
    }

    @Value
    private static class ExecResult {
        long pid;
        Integer exitValue;
    }
}
