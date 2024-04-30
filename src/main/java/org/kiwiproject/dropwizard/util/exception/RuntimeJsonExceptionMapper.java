package org.kiwiproject.dropwizard.util.exception;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import lombok.extern.slf4j.Slf4j;
import org.kiwiproject.jaxrs.exception.JaxrsBadRequestException;
import org.kiwiproject.jaxrs.exception.JaxrsException;
import org.kiwiproject.jaxrs.exception.JaxrsExceptionMapper;
import org.kiwiproject.json.RuntimeJsonException;

/**
 * Map {@link RuntimeJsonException} to {@link Response}.
 * <p>
 * If the cause of the {@link RuntimeJsonException} is a {@link JsonProcessingException} then
 * the behavior is the same as {@link JsonProcessingExceptionMapper}. Otherwise, the mapped
 * response is a 500 Internal Server Error.
 * <p>
 */
@Slf4j
public class RuntimeJsonExceptionMapper implements ExceptionMapper<RuntimeJsonException> {

    public static final String DEFAULT_MSG = "Unable to process JSON";

    @Override
    public Response toResponse(RuntimeJsonException runtimeJsonException) {
        var throwable = runtimeJsonException.getCause();

        if (throwable instanceof JsonProcessingException exception) {
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

        LOG.warn("Cause of RuntimeJsonException was not JsonProcessingException, but was {}. Returning a 500.",
                throwable.getClass().getName());
        return JaxrsExceptionMapper.buildResponse(JaxrsException.buildJaxrsException(throwable));
    }

}
