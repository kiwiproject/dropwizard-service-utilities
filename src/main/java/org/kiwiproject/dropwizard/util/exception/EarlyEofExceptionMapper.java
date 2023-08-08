package org.kiwiproject.dropwizard.util.exception;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.io.EofException;
import org.kiwiproject.jaxrs.exception.JaxrsBadRequestException;
import org.kiwiproject.jaxrs.exception.JaxrsException;
import org.kiwiproject.jaxrs.exception.JaxrsExceptionMapper;

/**
 * Override default Dropwizard mapper to use kiwi's {@link org.kiwiproject.jaxrs.exception.ErrorMessage ErrorMessage}.
 * The response entity is built using {@link JaxrsExceptionMapper#buildResponseEntity(JaxrsException)}.
 */
@Slf4j
@Provider
public class EarlyEofExceptionMapper implements ExceptionMapper<EofException> {

    @Override
    public Response toResponse(EofException exception) {
        LOG.debug("EOF Exception encountered - client disconnected during stream processing.", exception);
        return JaxrsExceptionMapper.buildResponse(new JaxrsBadRequestException(exception));
    }
}
