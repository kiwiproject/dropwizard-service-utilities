package org.kiwiproject.dropwizard.util.exception;

import static java.lang.String.format;

import com.google.common.annotations.VisibleForTesting;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;
import org.kiwiproject.jaxrs.exception.JaxrsBadRequestException;
import org.kiwiproject.jaxrs.exception.JaxrsConflictException;
import org.kiwiproject.jaxrs.exception.JaxrsException;
import org.kiwiproject.jaxrs.exception.JaxrsExceptionMapper;
import org.kiwiproject.jaxrs.exception.WebApplicationExceptionMapper;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Override default Dropwizard mapper to use kiwi's {@link org.kiwiproject.jaxrs.exception.ErrorMessage ErrorMessage}.
 * Catches all exceptions that don't have an explicit ExceptionMapper defined for them. The response entity is built
 * using {@link JaxrsExceptionMapper#buildResponseEntity(JaxrsException)}.
 *
 * @param <E> the exception to be mapped
 */
@Slf4j
@Provider
public class LoggingExceptionMapper<E extends Throwable> implements ExceptionMapper<E> {

    @VisibleForTesting
    static final String MSG_DB_OPTIMISTIC = "Unable to update data. You have a stale copy; refresh and try again!";

    @VisibleForTesting
    static final String MSG_DB_INVALID = "Unable to save data. Your data is invalid or not unique!";

    private enum DataExceptionCategory {
        OPTIMISTIC_LOCKING,
        DATA_INTEGRITY
    }

    private static final Map<String, DataExceptionCategory> DATA_EXCEPTIONS = Map.of(
            "org.springframework.dao.OptimisticLockingFailureException", DataExceptionCategory.OPTIMISTIC_LOCKING,
            "org.hibernate.dialect.lock.OptimisticEntityLockException", DataExceptionCategory.OPTIMISTIC_LOCKING,
            "org.springframework.dao.DataIntegrityViolationException", DataExceptionCategory.DATA_INTEGRITY,
            "org.hibernate.exception.ConstraintViolationException", DataExceptionCategory.DATA_INTEGRITY
    );

    @Override
    public Response toResponse(E exception) {
        if (exception instanceof WebApplicationException webApplicationException) {
            // this shouldn't happen as we have a registered exception mapper for this class
            return new WebApplicationExceptionMapper().toResponse(webApplicationException);
        }

        return responseFor(exception);
    }

    private Response responseFor(E exception) {
        var exceptionClassName = exception.getClass().getName();

        if (DATA_EXCEPTIONS.containsKey(exceptionClassName)) {
            var category = DATA_EXCEPTIONS.get(exceptionClassName);
            return dataAccessExceptionResponse(exception, category);
        }

        return logExceptionResponse(exception);
    }

    private Response dataAccessExceptionResponse(E exception, DataExceptionCategory category) {
        return switch (category) {
            case OPTIMISTIC_LOCKING -> buildOptimisticLockingResponse(exception);
            case DATA_INTEGRITY -> buildDataIntegrityResponse(exception);
        };
    }

    private static <E extends Throwable> Response buildOptimisticLockingResponse(E exception) {
        var r = JaxrsExceptionMapper.buildResponse(new JaxrsConflictException(MSG_DB_OPTIMISTIC));
        LOG.warn(MSG_DB_OPTIMISTIC, exception);
        return r;
    }

    private static <E extends Throwable> Response buildDataIntegrityResponse(E exception) {
        var r = JaxrsExceptionMapper.buildResponse(new JaxrsBadRequestException(MSG_DB_INVALID));
        LOG.warn(MSG_DB_INVALID, exception);
        return r;
    }

    private Response logExceptionResponse(E exception) {
        var id = ThreadLocalRandom.current().nextLong();
        LOG.error(formatLogMessage(id), exception);
        return JaxrsExceptionMapper.buildResponse(new JaxrsException(formatErrorMessage(id), exception));
    }

    private static String formatErrorMessage(long id) {
        return format("There was an error processing your request. It has been logged (ID %016x).", id);
    }

    private static String formatLogMessage(long id) {
        return format("Error handling a request: %016x", id);
    }
}
