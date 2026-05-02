package org.kiwiproject.dropwizard.util.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kiwiproject.dropwizard.util.exception.ErrorMessageAssertions.assertAndGetErrorMessage;
import static org.kiwiproject.test.jaxrs.JaxrsTestHelper.assertInternalServerErrorResponse;
import static org.kiwiproject.test.logback.InMemoryAppenderAssertions.assertThatAppender;

import org.jdbi.v3.core.CloseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.kiwiproject.test.logback.InMemoryAppender;
import org.kiwiproject.test.logback.InMemoryAppenderExtension;

import java.sql.SQLException;

@DisplayName("LoggingJdbiExceptionMapper")
class LoggingJdbiExceptionMapperTest {

    @RegisterExtension
    private final InMemoryAppenderExtension inMemoryAppenderExtension =
            new InMemoryAppenderExtension(LoggingJdbiExceptionMapper.class);

    private InMemoryAppender appender;

    private final LoggingJdbiExceptionMapper mapper = new LoggingJdbiExceptionMapper();

    @BeforeEach
    void setUp() {
        appender = inMemoryAppenderExtension.appender();
    }

    @Test
    void shouldReturnInternalServerErrorResponse_WhenCauseIsNotSQLException() {
        var cause = new RuntimeException("underlying error");
        var exception = new CloseException("jdbi error", cause);

        var response = mapper.toResponse(exception);

        assertInternalServerErrorResponse(response);

        var errorMessage = assertAndGetErrorMessage(response);
        assertThat(errorMessage.getMessage()).startsWith("There was an error processing your request");
    }

    @Test
    void shouldReturnInternalServerErrorResponse_WhenCauseIsSQLException() {
        var sqlException = new SQLException("sql error");
        var exception = new CloseException("jdbi error", sqlException);

        var response = mapper.toResponse(exception);

        assertInternalServerErrorResponse(response);

        var errorMessage = assertAndGetErrorMessage(response);
        assertThat(errorMessage.getMessage()).startsWith("There was an error processing your request");
    }

    @Test
    void shouldReturnInternalServerErrorResponse_WhenCauseIsSQLExceptionChain() {
        var root = new SQLException("root sql cause");
        root.setNextException(new SQLException("chained sql error"));
        var exception = new CloseException("jdbi error", root);

        var response = mapper.toResponse(exception);

        assertInternalServerErrorResponse(response);

        var errorMessage = assertAndGetErrorMessage(response);
        assertThat(errorMessage.getMessage()).startsWith("There was an error processing your request");
    }

    @Test
    void shouldReturnInternalServerErrorResponse_WhenNoCause() {
        var exception = new CloseException("jdbi error with no cause", null);

        var response = mapper.toResponse(exception);

        assertInternalServerErrorResponse(response);

        var errorMessage = assertAndGetErrorMessage(response);
        assertThat(errorMessage.getMessage()).startsWith("There was an error processing your request");
    }

    @Test
    void shouldLogJdbiException_WhenCauseIsNotSQLException() {
        var cause = new RuntimeException("underlying error");
        var exception = new CloseException("jdbi error", cause);

        mapper.toResponse(exception);

        var events = assertThatAppender(appender).hasNumberOfLoggingEventsAndGet(1);
        assertThat(events.get(0).getThrowableProxy().getMessage()).isEqualTo("jdbi error");
    }

    @Test
    void shouldLogSingleSQLException_WhenCauseIsSQLException() {
        var sqlException = new SQLException("sql error");
        var exception = new CloseException("jdbi error", sqlException);

        mapper.toResponse(exception);

        var events = assertThatAppender(appender).hasNumberOfLoggingEventsAndGet(1);
        assertThat(events.get(0).getThrowableProxy().getMessage()).isEqualTo("sql error");
    }

    @Test
    void shouldLogEachExceptionInSQLExceptionChain_WhenCauseIsSQLExceptionChain() {
        var root = new SQLException("root sql cause");
        root.setNextException(new SQLException("chained sql error"));
        var exception = new CloseException("jdbi error", root);

        mapper.toResponse(exception);

        var events = assertThatAppender(appender).hasNumberOfLoggingEventsAndGet(2);
        assertThat(events.get(0).getThrowableProxy().getMessage()).isEqualTo("root sql cause");
        assertThat(events.get(1).getThrowableProxy().getMessage()).isEqualTo("chained sql error");
    }

    @Test
    void shouldLogJdbiException_WhenNoCause() {
        var exception = new CloseException("jdbi error with no cause", null);

        mapper.toResponse(exception);

        var events = assertThatAppender(appender).hasNumberOfLoggingEventsAndGet(1);
        assertThat(events.get(0).getThrowableProxy().getMessage()).isEqualTo("jdbi error with no cause");
    }
}
