package org.kiwiproject.dropwizard.util.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import lombok.extern.slf4j.Slf4j;
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

    public static final String DEFAULT_MSG = JsonExceptionMappers.DEFAULT_MSG;

    @Override
    public Response toResponse(RuntimeJsonException runtimeJsonException) {
        var throwable = runtimeJsonException.getCause();

        if (throwable instanceof JsonProcessingException jsonProcessingException) {
            return JsonExceptionMappers.toResponse(jsonProcessingException);
        }

        LOG.warn("Cause of RuntimeJsonException was not JsonProcessingException, but was {}. Returning a 500.",
                throwable.getClass().getName());
        return JaxrsExceptionMapper.buildResponse(JaxrsException.buildJaxrsException(throwable));
    }

}
