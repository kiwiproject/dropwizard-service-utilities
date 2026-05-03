package org.kiwiproject.dropwizard.util.exception;

import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

import java.sql.SQLException;

/**
 * Override default Dropwizard mapper to use kiwi's {@link org.kiwiproject.jaxrs.exception.ErrorMessage ErrorMessage}.
 * Iterates through the {@link SQLException} chain to log all causes.
 * The response entity is built using {@link org.kiwiproject.jaxrs.exception.JaxrsExceptionMapper#buildResponseEntity(org.kiwiproject.jaxrs.exception.JaxrsException) JaxrsExceptionMapper#buildResponseEntity}.
 */
@Slf4j
@Provider
public class LoggingSQLExceptionMapper extends LoggingExceptionMapper<SQLException> {

    @Override
    protected void logException(long id, SQLException exception) {
        var message = formatLogMessage(id);
        for (var throwable : exception) {
            LOG.error(message, throwable);
        }
    }
}
