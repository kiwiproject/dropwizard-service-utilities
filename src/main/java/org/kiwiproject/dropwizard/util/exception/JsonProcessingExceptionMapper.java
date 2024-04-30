package org.kiwiproject.dropwizard.util.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.kiwiproject.jaxrs.exception.JaxrsException;
import org.kiwiproject.jaxrs.exception.JaxrsExceptionMapper;

/**
 * Override default Dropwizard mapper to use kiwi's {@link org.kiwiproject.jaxrs.exception.ErrorMessage ErrorMessage}.
 * The response entity is built using {@link JaxrsExceptionMapper#buildResponseEntity(JaxrsException)}.
 */
@Provider
public class JsonProcessingExceptionMapper implements ExceptionMapper<JsonProcessingException> {

    public static final String DEFAULT_MSG = JsonExceptionMappers.DEFAULT_MSG;

    @Override
    public Response toResponse(JsonProcessingException exception) {
        return JsonExceptionMappers.toResponse(exception);
    }
}
