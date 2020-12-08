package org.kiwiproject.dropwizard.util.exception;

import static org.kiwiproject.test.jaxrs.JaxrsTestHelper.assertNotFoundResponse;

import io.dropwizard.jersey.optional.EmptyOptionalException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("EmptyOptionalExceptionMapper")
class EmptyOptionalExceptionMapperTest {

    @Test
    void shouldReturnNotFoundResponse() {
        var ex = EmptyOptionalException.INSTANCE;
        var response = new EmptyOptionalExceptionMapper().toResponse(ex);
        assertNotFoundResponse(response);
    }
}
