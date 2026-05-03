package org.kiwiproject.dropwizard.util.exception;

import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.JdbiException;

import java.sql.SQLException;

/**
 * Override default Dropwizard mapper to use kiwi's {@link org.kiwiproject.jaxrs.exception.ErrorMessage ErrorMessage}.
 * If the {@link JdbiException} cause is a {@link SQLException}, iterates through the SQL exception chain to log all
 * causes; otherwise logs the exception normally.
 * The response entity is built using {@link org.kiwiproject.jaxrs.exception.JaxrsExceptionMapper#buildResponseEntity(org.kiwiproject.jaxrs.exception.JaxrsException) JaxrsExceptionMapper#buildResponseEntity}.
 */
@Slf4j
@Provider
public class LoggingJdbiExceptionMapper extends LoggingExceptionMapper<JdbiException> {

    @Override
    protected void logException(long id, JdbiException exception) {
        var cause = exception.getCause();
        if (cause instanceof SQLException sqlException) {
            var message = formatLogMessage(id);
            for (var throwable : sqlException) {
                LOG.error(message, throwable);
            }
        } else {
            LOG.error(formatLogMessage(id), exception);
        }
    }
}
