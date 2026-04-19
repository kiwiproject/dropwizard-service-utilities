package org.kiwiproject.dropwizard.util.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.kiwiproject.test.junit.jupiter.AsyncModeDisablingExtension;
import org.kiwiproject.test.junit.jupiter.ClearBoxTest;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@DisplayName("ExecuteJobTask")
class ExecuteJobTaskTest {

    @Nested
    class TaskNameOrDefault {

        @ClearBoxTest
        void shouldReturnProvidedTaskName() {
            assertThat(ExecuteJobTask.taskNameOrDefault("myTask", "Some Job")).isEqualTo("myTask");
        }

        @ClearBoxTest
        void shouldDeriveTaskNameFromJobNameWhenTaskNameIsNull() {
            assertThat(ExecuteJobTask.taskNameOrDefault(null, "Execute My Job")).isEqualTo("executeMyJob");
        }

        @ClearBoxTest
        void shouldDeriveTaskNameFromJobNameWhenTaskNameIsBlank() {
            assertThat(ExecuteJobTask.taskNameOrDefault("  ", "Execute My Job")).isEqualTo("executeMyJob");
        }

        @ClearBoxTest
        void shouldDeriveTaskNameFromJobNameWithMultipleSpaces() {
            assertThat(ExecuteJobTask.taskNameOrDefault(null, "Execute  My  Job")).isEqualTo("executeMyJob");
        }

        @ClearBoxTest
        void shouldDeriveTaskNameFromJobNameWithLeadingAndTrailingSpaces() {
            assertThat(ExecuteJobTask.taskNameOrDefault(null, "  Execute My Job  ")).isEqualTo("executeMyJob");
        }

        @ClearBoxTest
        void shouldThrowWhenTaskNameIsBlankAndJobNameIsBlank() {
            assertThatThrownBy(() -> ExecuteJobTask.taskNameOrDefault("  ", "  "))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @ClearBoxTest
        void shouldThrowWhenTaskNameIsNullAndJobNameIsBlank() {
            assertThatThrownBy(() -> ExecuteJobTask.taskNameOrDefault(null, ""))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class Construction {

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
        void shouldBuildSuccessfully() {
            assertThatCode(() -> ExecuteJobTask.builder()
                    .job(() -> {})
                    .jobName("Test Job")
                    .taskConfig(ExecuteJobTaskConfig.canAlwaysRun(executor))
                    .build())
                    .doesNotThrowAnyException();
        }

        @Test
        void shouldDeriveTaskNameFromJobNameAsCamelCase() {
            var task = ExecuteJobTask.builder()
                    .job(() -> {})
                    .jobName("Execute My Job")
                    .taskConfig(ExecuteJobTaskConfig.canAlwaysRun(executor))
                    .build();
            assertThat(task.getName()).isEqualTo("executeMyJob");
        }

        @Test
        void shouldUseProvidedTaskName() {
            var task = ExecuteJobTask.builder()
                    .job(() -> {})
                    .jobName("Execute My Job")
                    .taskName("custom-task")
                    .taskConfig(ExecuteJobTaskConfig.canAlwaysRun(executor))
                    .build();
            assertThat(task.getName()).isEqualTo("custom-task");
        }

        @Test
        void shouldRequireJob() {
            var builder = ExecuteJobTask.builder()
                    .jobName("Test Job")
                    .taskConfig(ExecuteJobTaskConfig.canAlwaysRun(executor));
            assertThatThrownBy(builder::build)
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldRequireJobName() {
            var builder = ExecuteJobTask.builder()
                    .job(() -> {})
                    .taskName("my-task")
                    .taskConfig(ExecuteJobTaskConfig.canAlwaysRun(executor));
            assertThatThrownBy(builder::build)
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldRequireTaskConfig() {
            var builder = ExecuteJobTask.builder()
                    .job(() -> {})
                    .jobName("Test Job");
            assertThatThrownBy(builder::build)
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @ExtendWith(AsyncModeDisablingExtension.class)
    class Execute {

        private ExecutorService executor;
        private StringWriter stringWriter;
        private PrintWriter output;

        @BeforeEach
        void setUp() {
            executor = Executors.newSingleThreadExecutor();
            stringWriter = new StringWriter();
            output = new PrintWriter(stringWriter);
        }

        @AfterEach
        void tearDown() {
            executor.shutdownNow();
        }

        private ExecuteJobTask buildTask(Runnable job) {
            return ExecuteJobTask.builder()
                    .job(job)
                    .jobName("Test Job")
                    .taskConfig(ExecuteJobTaskConfig.canAlwaysRun(executor))
                    .build();
        }

        private String outputText() {
            return stringWriter.toString().strip();
        }

        @Nested
        class WhenAlreadyRunning {

            @Test
            void shouldSkipJobAndPrintAlreadyRunningMessage() throws Exception {
                var jobRan = new AtomicBoolean();
                var task = buildTask(() -> jobRan.set(true));
                task.running.set(true);

                task.execute(Map.of(), output);

                assertAll(
                        () -> assertThat(jobRan.get()).isFalse(),
                        () -> assertThat(outputText()).isEqualTo("Task for Test Job is already running. Skipping."),
                        () -> assertThat(task.running.get()).isTrue()
                );
            }
        }

        @Nested
        class WhenCannotRun {

            @Test
            void shouldPrintMessageAndResetRunning() throws Exception {
                var task = ExecuteJobTask.builder()
                        .job(() -> {})
                        .jobName("Test Job")
                        .taskConfig(ExecuteJobTaskConfig.builder()
                                .canRun(() -> false)
                                .executorService(executor)
                                .build())
                        .build();

                task.execute(Map.of(), output);

                assertAll(
                        () -> assertThat(outputText()).isEqualTo("Not executing Test Job (canRun returned false)"),
                        () -> assertThat(task.running.get()).isFalse()
                );
            }

            @Test
            void shouldThrowProvidedExceptionAndResetRunning() {
                var ex = new IllegalStateException("not the leader");
                var task = ExecuteJobTask.builder()
                        .job(() -> {})
                        .jobName("Test Job")
                        .taskConfig(ExecuteJobTaskConfig.builder()
                                .canRun(() -> false)
                                .cannotRunExceptionProvider(() -> ex)
                                .executorService(executor)
                                .build())
                        .build();

                assertThatThrownBy(() -> task.execute(Map.of(), output))
                        .isSameAs(ex);
                assertThat(task.running.get()).isFalse();
            }

            @Test
            void shouldResetRunningAndRethrowWhenCanRunThrows() {
                var cause = new RuntimeException("canRun exploded");
                var task = ExecuteJobTask.builder()
                        .job(() -> {})
                        .jobName("Test Job")
                        .taskConfig(ExecuteJobTaskConfig.builder()
                                .canRun(() -> { throw cause; })
                                .executorService(executor)
                                .build())
                        .build();

                assertThatThrownBy(() -> task.execute(Map.of(), output))
                        .isSameAs(cause);
                assertThat(task.running.get()).isFalse();
            }
        }

        @Nested
        class Synchronous {

            @Test
            void shouldExecuteJobAndPrintSuccessMessage() throws Exception {
                var jobRan = new AtomicBoolean();
                var task = buildTask(() -> jobRan.set(true));

                task.execute(Map.of("sync", List.of("true")), output);

                assertAll(
                        () -> assertThat(jobRan.get()).isTrue(),
                        () -> assertThat(outputText()).startsWith("Successfully executed Test Job in "),
                        () -> assertThat(task.running.get()).isFalse()
                );
            }

            @ParameterizedTest
            @ValueSource(strings = {"true", "on", "y", "t", "yes", "True", "YES", "ON"})
            void shouldTreatAllDocumentedTruthyValuesAsSynchronous(String syncValue) throws Exception {
                var jobRan = new AtomicBoolean();
                var task = buildTask(() -> jobRan.set(true));

                task.execute(Map.of("sync", List.of(syncValue)), output);

                assertAll(
                        () -> assertThat(jobRan.get()).isTrue(),
                        () -> assertThat(outputText()).startsWith("Successfully executed Test Job in ")
                );
            }

            @Test
            void shouldThrowJobExceptionAndResetRunning() {
                var ex = new RuntimeException("job failed");
                var task = buildTask(() -> { throw ex; });

                assertThatThrownBy(() -> task.execute(Map.of("sync", List.of("true")), output))
                        .isSameAs(ex);
                assertThat(task.running.get()).isFalse();
            }
        }

        @Nested
        class Asynchronous {

            @Test
            void shouldDefaultToAsyncWhenSyncParamIsAbsent() throws Exception {
                var task = buildTask(() -> {});

                task.execute(Map.of(), output);

                assertThat(outputText()).startsWith("Started executing Test Job in background (asyncId: ");
            }

            @Test
            void shouldDefaultToAsyncWhenSyncParamIsFalse() throws Exception {
                var task = buildTask(() -> {});

                task.execute(Map.of("sync", List.of("false")), output);

                assertThat(outputText()).startsWith("Started executing Test Job in background (asyncId: ");
            }

            @Test
            void shouldExecuteJobAndPrintAsyncMessage() throws Exception {
                var jobRan = new AtomicBoolean();
                var task = buildTask(() -> jobRan.set(true));

                task.execute(Map.of(), output);

                assertAll(
                        () -> assertThat(jobRan.get()).isTrue(),
                        () -> assertThat(outputText()).startsWith("Started executing Test Job in background (asyncId: "),
                        () -> assertThat(task.running.get()).isFalse()
                );
            }

            @Test
            void shouldNotThrowAndResetRunningWhenJobThrows() {
                var task = buildTask(() -> { throw new RuntimeException("async job failed"); });

                assertThatCode(() -> task.execute(Map.of(), output))
                        .doesNotThrowAnyException();

                assertAll(
                        () -> assertThat(outputText()).startsWith("Started executing Test Job in background (asyncId: "),
                        () -> assertThat(task.running.get()).isFalse()
                );
            }

            @Test
            void shouldResetRunningAndRethrowWhenExecutorRejectsSubmission() {
                var rejectedExecutor = Executors.newSingleThreadExecutor();
                rejectedExecutor.shutdownNow();
                var task = ExecuteJobTask.builder()
                        .job(() -> {})
                        .jobName("Test Job")
                        .taskConfig(ExecuteJobTaskConfig.canAlwaysRun(rejectedExecutor))
                        .build();

                var params = Map.<String, List<String>>of();
                assertThatThrownBy(() -> task.execute(params, output))
                        .isInstanceOf(RuntimeException.class);
                assertThat(task.running.get()).isFalse();
            }
        }
    }
}
