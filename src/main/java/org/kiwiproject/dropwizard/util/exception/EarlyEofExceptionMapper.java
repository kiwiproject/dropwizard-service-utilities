package org.kiwiproject.dropwizard.util.exception;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.io.EofException;
import org.kiwiproject.jaxrs.exception.JaxrsBadRequestException;
import org.kiwiproject.jaxrs.exception.JaxrsExceptionMapper;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

/**
 * Override default Dropwizard mapper to use common error structure.
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
