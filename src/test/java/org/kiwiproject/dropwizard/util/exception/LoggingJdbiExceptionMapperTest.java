package org.kiwiproject.dropwizard.util.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kiwiproject.dropwizard.util.exception.ErrorMessageAssertions.assertAndGetErrorMessage;
import static org.kiwiproject.test.jaxrs.JaxrsTestHelper.assertInternalServerErrorResponse;

import org.jdbi.v3.core.CloseException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

@DisplayName("LoggingJdbiExceptionMapper")
class LoggingJdbiExceptionMapperTest {

    private final LoggingJdbiExceptionMapper mapper = new LoggingJdbiExceptionMapper();

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
}
