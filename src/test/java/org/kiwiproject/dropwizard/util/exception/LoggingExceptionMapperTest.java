package org.kiwiproject.dropwizard.util.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kiwiproject.dropwizard.util.exception.ErrorMessageAssertions.assertAndGetErrorMessage;
import static org.kiwiproject.test.jaxrs.JaxrsTestHelper.assertBadRequest;
import static org.kiwiproject.test.jaxrs.JaxrsTestHelper.assertConflict;
import static org.kiwiproject.test.jaxrs.JaxrsTestHelper.assertInternalServerErrorResponse;

import org.hibernate.dialect.lock.OptimisticEntityLockException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;

import javax.ws.rs.WebApplicationException;
import java.sql.SQLException;

@SuppressWarnings("rawtypes")
@DisplayName("LoggingExceptionMapper")
class LoggingExceptionMapperTest {

    private final LoggingExceptionMapper mapper = new LoggingExceptionMapper<>() {
    };

    @SuppressWarnings("unchecked")
    @Test
    void shouldProcess_AnyNonMappedException() {
        var exception = new RuntimeException("oops");
        var response = mapper.toResponse(exception);
        var errorMessage = assertAndGetErrorMessage(response);

        assertInternalServerErrorResponse(response);
        assertThat(errorMessage.getMessage()).startsWith("There was an error processing your request");
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldProcess_SpringOptimisticLockingFailureException() {
        var exception = new OptimisticLockingFailureException("optimistic lock error");
        var response = mapper.toResponse(exception);
        var errorMessage = assertAndGetErrorMessage(response);

        assertConflict(response);
        assertThat(errorMessage.getMessage()).isEqualTo(LoggingExceptionMapper.MSG_DB_OPTIMISTIC);
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldProcess_HibernateOptimisticEntityLockException() {
        var entity = new Object();
        var exception = new OptimisticEntityLockException(entity, "optimistic lock error");
        var response = mapper.toResponse(exception);
        var errorMessage = assertAndGetErrorMessage(response);

        assertConflict(response);
        assertThat(errorMessage.getMessage()).isEqualTo(LoggingExceptionMapper.MSG_DB_OPTIMISTIC);
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldProcess_SpringDataIntegrityViolationException() {
        var exception = new DataIntegrityViolationException("data integrity violation");
        var response = mapper.toResponse(exception);
        var errorMessage = assertAndGetErrorMessage(response);

        assertBadRequest(response);
        assertThat(errorMessage.getMessage()).isEqualTo(LoggingExceptionMapper.MSG_DB_INVALID);
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldProcess_HibernateConstraintViolationException() {
        var sqlException = new SQLException();
        var exception = new ConstraintViolationException("constraint violation", sqlException, "constraint12345");
        var response = mapper.toResponse(exception);
        var errorMessage = assertAndGetErrorMessage(response);

        assertBadRequest(response);
        assertThat(errorMessage.getMessage()).isEqualTo(LoggingExceptionMapper.MSG_DB_INVALID);
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldProcess_WebApplicationException() {
        var exception = new WebApplicationException("oops");
        var response = mapper.toResponse(exception);
        var errorMessage = assertAndGetErrorMessage(response);

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(errorMessage.getMessage()).startsWith("oops");
    }

}
