package org.kiwiproject.dropwizard.util.exception;

import static java.lang.String.format;

import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;
import org.kiwiproject.jaxrs.exception.JaxrsBadRequestException;
import org.kiwiproject.jaxrs.exception.JaxrsException;
import org.kiwiproject.jaxrs.exception.JaxrsExceptionMapper;
import org.kiwiproject.jaxrs.exception.WebApplicationExceptionMapper;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Override default Dropwizard mapper to use common error structure. Catches all exceptions that don't have an explicit
 * ExceptionMapper defined for them.
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

    @Override
    public Response toResponse(E exception) {
        Response r;

        if (exception instanceof WebApplicationException) {
            // this shouldn't happen as we have a registered exception mapper for this class
            r = new WebApplicationExceptionMapper().toResponse((WebApplicationException) exception);
        } else {
            var exceptionName = exception.getClass().getCanonicalName();

            // don't want to load spring libraries to create it's own exception mapper but don't want alot of
            // services to have to create a duplicate DataAccessExceptionMapper either so ...
            r = exceptionName.startsWith("org.springframework.dao.")
                    ? dataAccessExceptionResponse(exceptionName, exception)
                    : logExceptionResponse(exception);
        }

        return r;
    }

    @VisibleForTesting
    Response dataAccessExceptionResponse(String className, E exception) {
        Response r;
        switch (className) {
            case "org.springframework.dao.OptimisticLockingFailureException":
                r = JaxrsExceptionMapper.buildResponse(new JaxrsBadRequestException(MSG_DB_OPTIMISTIC));
                LOG.warn(MSG_DB_OPTIMISTIC, exception);
                break;
            case "org.springframework.dao.DataIntegrityViolationException":
                r = JaxrsExceptionMapper.buildResponse(new JaxrsBadRequestException(MSG_DB_INVALID));
                LOG.warn(MSG_DB_INVALID, exception);
                break;
            default:
                r = logExceptionResponse(exception);
        }

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
