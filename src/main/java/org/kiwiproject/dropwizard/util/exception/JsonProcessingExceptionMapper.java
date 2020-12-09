package org.kiwiproject.dropwizard.util.exception;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException;
import lombok.extern.slf4j.Slf4j;
import org.kiwiproject.jaxrs.exception.JaxrsBadRequestException;
import org.kiwiproject.jaxrs.exception.JaxrsException;
import org.kiwiproject.jaxrs.exception.JaxrsExceptionMapper;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

/**
 * Override default Dropwizard mapper to use kiwi's {@link org.kiwiproject.jaxrs.exception.ErrorMessage ErrorMessage}.
 * The the response entity is built using {@link JaxrsExceptionMapper#buildResponseEntity(JaxrsException)}.
 */
@Slf4j
@Provider
public class JsonProcessingExceptionMapper implements ExceptionMapper<JsonProcessingException> {

    public static final String DEFAULT_MSG = "Unable to process JSON";

    @Override
    public Response toResponse(JsonProcessingException exception) {
        JaxrsException e;

        if (exception instanceof JsonGenerationException || exception instanceof InvalidDefinitionException) {
            LOG.warn("Error generating JSON", exception);
            e = new JaxrsException(exception);
        } else {
            var message = exception.getOriginalMessage();

            if (message.startsWith("No suitable constructor found")) {
                LOG.error("Unable to deserialize the specific type", exception);
                e = new JaxrsException(exception);
            } else {
                LOG.debug(DEFAULT_MSG, exception);
                e = new JaxrsBadRequestException(message, exception);
            }
        }

        return JaxrsExceptionMapper.buildResponse(e);
    }
}
