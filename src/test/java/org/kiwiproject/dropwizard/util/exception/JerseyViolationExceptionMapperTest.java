package org.kiwiproject.dropwizard.util.exception;

import static org.kiwiproject.test.jaxrs.JaxrsTestHelper.assertUnprocessableEntity;

import io.dropwizard.jersey.validation.JerseyViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

@DisplayName("JerseyViolationExceptionMapper")
class JerseyViolationExceptionMapperTest {

    @Test
    void shouldReturn422_UnprocessableEntity_Response() {
        var ex = new JerseyViolationException(Set.of(), null);
        var response = new JerseyViolationExceptionMapper().toResponse(ex);
        assertUnprocessableEntity(response);
    }
}
