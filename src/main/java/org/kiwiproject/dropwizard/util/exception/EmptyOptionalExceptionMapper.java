package org.kiwiproject.dropwizard.util.exception;

import io.dropwizard.jersey.optional.EmptyOptionalException;
import org.kiwiproject.jaxrs.exception.JaxrsException;
import org.kiwiproject.jaxrs.exception.JaxrsExceptionMapper;
import org.kiwiproject.jaxrs.exception.JaxrsNotFoundException;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

/**
 * Override default Dropwizard mapper to use kiwi's {@link org.kiwiproject.jaxrs.exception.ErrorMessage ErrorMessage}.
 * The response entity is built using {@link JaxrsExceptionMapper#buildResponseEntity(JaxrsException)}.
 */
@Provider
public class EmptyOptionalExceptionMapper implements ExceptionMapper<EmptyOptionalException> {

    @Override
    public Response toResponse(EmptyOptionalException exception) {
        return JaxrsExceptionMapper.buildResponse(new JaxrsNotFoundException(exception));
    }
}
