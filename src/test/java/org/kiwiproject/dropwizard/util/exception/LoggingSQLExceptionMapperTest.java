package org.kiwiproject.dropwizard.util.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kiwiproject.dropwizard.util.exception.ErrorMessageAssertions.assertAndGetErrorMessage;
import static org.kiwiproject.test.jaxrs.JaxrsTestHelper.assertInternalServerErrorResponse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

@DisplayName("LoggingSQLExceptionMapper")
class LoggingSQLExceptionMapperTest {

    private final LoggingSQLExceptionMapper mapper = new LoggingSQLExceptionMapper();

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
}
