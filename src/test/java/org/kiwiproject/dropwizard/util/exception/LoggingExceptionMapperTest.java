package org.kiwiproject.dropwizard.util.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kiwiproject.dropwizard.util.exception.ErrorMessageAssertion.assertAndGetErrorMessage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.ws.rs.WebApplicationException;

@SuppressWarnings("rawtypes")
@DisplayName("LoggingExceptionMapper")
class LoggingExceptionMapperTest {

    private final LoggingExceptionMapper mapper = new LoggingExceptionMapper<>() {};

    @SuppressWarnings("unchecked")
    @Test
    void shouldProcessAnyNonMappedException() {
        var exception = new RuntimeException("oops");
        var response = mapper.toResponse(exception);
        var errorMessage = assertAndGetErrorMessage(response);

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(errorMessage.getMessage()).startsWith("There was an error processing your request");
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldProcessSpringClasses() {
        var className = "org.springframework.dao.OptimisticLockingFailureException";
        var exception = new RuntimeException("oops");
        var response = mapper.dataAccessExceptionResponse(className, exception);
        var errorMessage = assertAndGetErrorMessage(response);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(errorMessage.getMessage()).isEqualTo(LoggingExceptionMapper.MSG_DB_OPTIMISTIC);

        className = "org.springframework.dao.DataIntegrityViolationException";
        response = mapper.dataAccessExceptionResponse(className, exception);
        errorMessage = assertAndGetErrorMessage(response);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(errorMessage.getMessage()).isEqualTo(LoggingExceptionMapper.MSG_DB_INVALID);

        className = "org.springframework.dao.SomeOtherException";
        response = mapper.dataAccessExceptionResponse(className, exception);
        errorMessage = assertAndGetErrorMessage(response);

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(errorMessage.getMessage()).startsWith("There was an error processing your request");
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldProcessWebApplicationException() {
        var exception = new WebApplicationException("oops");
        var response = mapper.toResponse(exception);
        var errorMessage = assertAndGetErrorMessage(response);

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(errorMessage.getMessage()).startsWith("oops");
    }

}
