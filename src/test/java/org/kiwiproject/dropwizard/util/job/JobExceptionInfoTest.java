package org.kiwiproject.dropwizard.util.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("JobExceptionInfo")
class JobExceptionInfoTest {

    @Test
    void shouldCreateJobExceptionInfo() {
        var jobExceptionInfo = new JobExceptionInfo("testType",
                "testMessage",
                "rootCauseType",
                "rootCauseMessage");

        assertAll(
                () -> assertThat(jobExceptionInfo.type()).isEqualTo("testType"),
                () -> assertThat(jobExceptionInfo.message()).isEqualTo("testMessage"),
                () -> assertThat(jobExceptionInfo.rootCauseType()).isEqualTo("rootCauseType"),
                () -> assertThat(jobExceptionInfo.rootCauseMessage()).isEqualTo("rootCauseMessage")
        );
    }

    @Test
    void shouldHandleNullValues() {
        var jobExceptionInfo = new JobExceptionInfo("testType", null, null, null);

        assertAll(
                () -> assertThat(jobExceptionInfo.type()).isEqualTo("testType"),
                () -> assertThat(jobExceptionInfo.message()).isNull(),
                () -> assertThat(jobExceptionInfo.rootCauseType()).isNull(),
                () -> assertThat(jobExceptionInfo.rootCauseMessage()).isNull()
        );
    }

    @Test
    void shouldRequireType() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new JobExceptionInfo(null, null, null, null))
                .withMessage("type must not be blank");
    }

    @Test
    void shouldReturnJobExceptionInfoFromException() {
        var exception = new Exception("This is an Exception", new RuntimeException("Caused by a RuntimeException"));
        var jobExceptionInfo = JobExceptionInfo.from(exception);

        assertAll(
                () -> assertThat(jobExceptionInfo.type()).isEqualTo("java.lang.Exception"),
                () -> assertThat(jobExceptionInfo.message()).isEqualTo("This is an Exception"),
                () -> assertThat(jobExceptionInfo.rootCauseType()).isEqualTo("java.lang.RuntimeException"),
                () -> assertThat(jobExceptionInfo.rootCauseMessage()).isEqualTo("Caused by a RuntimeException")
        );
    }
}
