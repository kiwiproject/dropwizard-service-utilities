package org.kiwiproject.dropwizard.util.exception;

import io.dropwizard.jersey.validation.JerseyViolationException;
import org.kiwiproject.jaxrs.exception.ConstraintViolationExceptionMapper;
import org.kiwiproject.jaxrs.exception.JaxrsException;
import org.kiwiproject.jaxrs.exception.JaxrsExceptionMapper;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

/**
 * Override default Dropwizard mapper to use kiwi's {@link org.kiwiproject.jaxrs.exception.ErrorMessage ErrorMessage}.
 * The the response entity is built using {@link JaxrsExceptionMapper#buildResponseEntity(JaxrsException)}.
 */
@Provider
public class JerseyViolationExceptionMapper implements ExceptionMapper<JerseyViolationException> {

    @Override
    public Response toResponse(JerseyViolationException exception) {
        return ConstraintViolationExceptionMapper.buildResponse(exception.getConstraintViolations());
    }
}
