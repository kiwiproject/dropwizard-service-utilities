package org.kiwiproject.dropwizard.util.exception;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException;
import jakarta.ws.rs.core.Response;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.kiwiproject.jaxrs.exception.JaxrsBadRequestException;
import org.kiwiproject.jaxrs.exception.JaxrsException;
import org.kiwiproject.jaxrs.exception.JaxrsExceptionMapper;

@UtilityClass
@Slf4j
class JsonExceptionMappers {

    static final String DEFAULT_MSG = "Unable to process JSON";

    static Response toResponse(JsonProcessingException exception) {
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
