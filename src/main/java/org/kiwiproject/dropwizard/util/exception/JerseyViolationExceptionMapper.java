package org.kiwiproject.dropwizard.util.exception;

import io.dropwizard.jersey.validation.JerseyViolationException;
import org.kiwiproject.jaxrs.exception.ConstraintViolationExceptionMapper;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

/**
 * Override default Dropwizard mapper to use common error structure.
 */
@Provider
public class JerseyViolationExceptionMapper implements ExceptionMapper<JerseyViolationException> {

    @Override
    public Response toResponse(JerseyViolationException exception) {
        return ConstraintViolationExceptionMapper.buildResponse(exception.getConstraintViolations());
    }
}
