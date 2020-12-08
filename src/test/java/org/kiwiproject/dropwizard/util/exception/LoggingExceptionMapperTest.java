package org.kiwiproject.dropwizard.util.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.kiwiproject.jaxrs.exception.ErrorMessage;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

@SuppressWarnings("rawtypes")
@DisplayName("LoggingExceptionMapper")
class LoggingExceptionMapperTest {

    private final LoggingExceptionMapper mapper = new LoggingExceptionMapper<>() {};

    @SuppressWarnings("unchecked")
    @Test
    void shouldProcessAnyNonMappedException() {
        var exception = new RuntimeException("oops");
        var response = mapper.toResponse(exception);
        var errorMessage = getErrorMessage(response);

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(errorMessage.getMessage()).startsWith("There was an error processing your request");
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldProcessSpringClasses() {
        var className = "org.springframework.dao.OptimisticLockingFailureException";
        var exception = new RuntimeException("oops");
        var response = mapper.dataAccessExceptionResponse(className, exception);
        var errorMessage = getErrorMessage(response);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(errorMessage.getMessage()).isEqualTo(LoggingExceptionMapper.MSG_DB_OPTIMISTIC);

        className = "org.springframework.dao.DataIntegrityViolationException";
        response = mapper.dataAccessExceptionResponse(className, exception);
        errorMessage = getErrorMessage(response);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(errorMessage.getMessage()).isEqualTo(LoggingExceptionMapper.MSG_DB_INVALID);

        className = "org.springframework.dao.SomeOtherException";
        response = mapper.dataAccessExceptionResponse(className, exception);
        errorMessage = getErrorMessage(response);

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(errorMessage.getMessage()).startsWith("There was an error processing your request");
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldProcessWebApplicationException() {
        var exception = new WebApplicationException("oops");
        var response = mapper.toResponse(exception);
        var errorMessage = getErrorMessage(response);

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(errorMessage.getMessage()).startsWith("oops");
    }

    @SuppressWarnings("unchecked")
    private ErrorMessage getErrorMessage(Response r) {
        Map entity = (Map) r.getEntity();
        List<ErrorMessage> errors = (List<ErrorMessage>) entity.get("errors");
        return errors.get(0);
    }
}
