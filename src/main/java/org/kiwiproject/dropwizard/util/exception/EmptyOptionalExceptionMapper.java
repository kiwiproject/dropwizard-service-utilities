package org.kiwiproject.dropwizard.util.exception;

import io.dropwizard.jersey.optional.EmptyOptionalException;
import org.kiwiproject.jaxrs.exception.JaxrsExceptionMapper;
import org.kiwiproject.jaxrs.exception.JaxrsNotFoundException;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

/**
 * Override default Dropwizard mapper to use common error structure.
 */
@Provider
public class EmptyOptionalExceptionMapper implements ExceptionMapper<EmptyOptionalException> {

    @Override
    public Response toResponse(EmptyOptionalException exception) {
        return JaxrsExceptionMapper.buildResponse(new JaxrsNotFoundException(exception));
    }
}
