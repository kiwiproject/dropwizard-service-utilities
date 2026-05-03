package org.kiwiproject.dropwizard.util.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kiwiproject.dropwizard.util.exception.ErrorMessageAssertions.assertAndGetErrorMessage;
import static org.kiwiproject.test.jaxrs.JaxrsTestHelper.assertInternalServerErrorResponse;
import static org.kiwiproject.test.logback.InMemoryAppenderAssertions.assertThatAppender;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.kiwiproject.test.logback.InMemoryAppender;
import org.kiwiproject.test.logback.InMemoryAppenderExtension;

import java.sql.SQLException;

@DisplayName("LoggingSQLExceptionMapper")
class LoggingSQLExceptionMapperTest {

    @RegisterExtension
    private final InMemoryAppenderExtension inMemoryAppenderExtension =
            new InMemoryAppenderExtension(LoggingSQLExceptionMapper.class);

    private InMemoryAppender appender;

    private final LoggingSQLExceptionMapper mapper = new LoggingSQLExceptionMapper();

    @BeforeEach
    void setUp() {
        appender = inMemoryAppenderExtension.appender();
    }

    @Test
    void shouldReturnInternalServerErrorResponse() {
        var exception = new SQLException("database error");
        var response = mapper.toResponse(exception);

        assertInternalServerErrorResponse(response);

        var errorMessage = assertAndGetErrorMessage(response);
        assertThat(errorMessage.getMessage()).startsWith("There was an error processing your request");
    }

    @Test
    void shouldHandleSQLExceptionChain() {
        var root = new SQLException("root cause");
        var chained = new SQLException("chained error");
        root.setNextException(chained);

        var response = mapper.toResponse(root);

        assertInternalServerErrorResponse(response);

        var errorMessage = assertAndGetErrorMessage(response);
        assertThat(errorMessage.getMessage()).startsWith("There was an error processing your request");
    }

    @Test
    void shouldLogSingleSQLException() {
        var exception = new SQLException("database error");
        mapper.toResponse(exception);

        var events = assertThatAppender(appender).hasNumberOfLoggingEventsAndGet(1);
        assertThat(events.get(0).getThrowableProxy().getMessage()).isEqualTo("database error");
    }

    @Test
    void shouldLogEachExceptionInSQLExceptionChain() {
        var root = new SQLException("root cause");
        var chained = new SQLException("chained error");
        root.setNextException(chained);

        mapper.toResponse(root);

        var events = assertThatAppender(appender).hasNumberOfLoggingEventsAndGet(2);
        assertThat(events.get(0).getThrowableProxy().getMessage()).isEqualTo("root cause");
        assertThat(events.get(1).getThrowableProxy().getMessage()).isEqualTo("chained error");
    }
}
