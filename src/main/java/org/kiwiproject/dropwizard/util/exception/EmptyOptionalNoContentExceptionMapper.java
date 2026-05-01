package org.kiwiproject.dropwizard.util.exception;

import io.dropwizard.jersey.optional.EmptyOptionalException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Override default Dropwizard mapper to return a 204 No Content response when an Optional is empty.
 * <p>
 * Unlike {@link EmptyOptionalExceptionMapper} which returns a 404 Not Found response, this mapper
 * returns 204 No Content, which is appropriate when an empty Optional is a valid outcome that should
 * not be treated as an error.
 * <p>
 * Note: Only one of {@link EmptyOptionalExceptionMapper} and {@link EmptyOptionalNoContentExceptionMapper}
 * should be registered at a time, as both handle {@link EmptyOptionalException}.
 */
@Provider
public class EmptyOptionalNoContentExceptionMapper implements ExceptionMapper<EmptyOptionalException> {

    @Override
    public Response toResponse(EmptyOptionalException exception) {
        return Response.noContent().build();
    }
}
