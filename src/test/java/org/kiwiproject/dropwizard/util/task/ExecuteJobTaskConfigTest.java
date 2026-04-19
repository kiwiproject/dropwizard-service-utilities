package org.kiwiproject.dropwizard.util.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@DisplayName("ExecuteJobTaskConfig")
class ExecuteJobTaskConfigTest {

    @Nested
    class Builder {

        private ExecutorService executor;

        @BeforeEach
        void setUp() {
            executor = Executors.newSingleThreadExecutor();
        }

        @AfterEach
        void tearDown() {
            executor.shutdownNow();
        }

        @Test
        void shouldRequireExecutorService() {
            var builder = ExecuteJobTaskConfig.builder();
            assertThatThrownBy(builder::build)
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldDefaultCanRunToAlwaysTrue() {
            var config = ExecuteJobTaskConfig.builder()
                    .executorService(executor)
                    .build();
            assertThat(config.canRun().getAsBoolean()).isTrue();
        }

        @Test
        void shouldDefaultCannotRunExceptionProviderToReturnsNull() {
            var config = ExecuteJobTaskConfig.builder()
                    .executorService(executor)
                    .build();
            assertThat(config.cannotRunExceptionProvider().get()).isNull();
        }

        @Test
        void shouldAcceptCustomCanRun() {
            var config = ExecuteJobTaskConfig.builder()
                    .canRun(() -> false)
                    .executorService(executor)
                    .build();
            assertThat(config.canRun().getAsBoolean()).isFalse();
        }

        @Test
        void shouldAcceptCustomCannotRunExceptionProvider() {
            var ex = new IllegalStateException("not allowed");
            var config = ExecuteJobTaskConfig.builder()
                    .cannotRunExceptionProvider(() -> ex)
                    .executorService(executor)
                    .build();
            assertThat(config.cannotRunExceptionProvider().get()).isSameAs(ex);
        }

        @Test
        void shouldUseProvidedExecutorService() {
            var config = ExecuteJobTaskConfig.builder()
                    .executorService(executor)
                    .build();
            assertThat(config.executorService()).isSameAs(executor);
        }
    }

    @Nested
    class CanAlwaysRun {

        private ExecutorService executor;

        @BeforeEach
        void setUp() {
            executor = Executors.newSingleThreadExecutor();
        }

        @AfterEach
        void tearDown() {
            executor.shutdownNow();
        }

        @Test
        void shouldRequireExecutorService() {
            assertThatThrownBy(() -> ExecuteJobTaskConfig.canAlwaysRun(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldCreateConfigThatAlwaysRunsWithNoExceptionAndCorrectExecutor() {
            var config = ExecuteJobTaskConfig.canAlwaysRun(executor);
            assertAll(
                    () -> assertThat(config.canRun().getAsBoolean()).isTrue(),
                    () -> assertThat(config.cannotRunExceptionProvider().get()).isNull(),
                    () -> assertThat(config.executorService()).isSameAs(executor)
            );
        }
    }

    @Nested
    class CanRunWhen {

        private ExecutorService executor;

        @BeforeEach
        void setUp() {
            executor = Executors.newSingleThreadExecutor();
        }

        @AfterEach
        void tearDown() {
            executor.shutdownNow();
        }

        @Test
        void shouldRequireCanRun() {
            assertThatThrownBy(() -> ExecuteJobTaskConfig.canRunWhen(null, executor))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldRequireExecutorService() {
            assertThatThrownBy(() -> ExecuteJobTaskConfig.canRunWhen(() -> true, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldUseProvidedCanRunCondition() {
            var config = ExecuteJobTaskConfig.canRunWhen(() -> false, executor);
            assertAll(
                    () -> assertThat(config.canRun().getAsBoolean()).isFalse(),
                    () -> assertThat(config.cannotRunExceptionProvider().get()).isNull(),
                    () -> assertThat(config.executorService()).isSameAs(executor)
            );
        }
    }
}
