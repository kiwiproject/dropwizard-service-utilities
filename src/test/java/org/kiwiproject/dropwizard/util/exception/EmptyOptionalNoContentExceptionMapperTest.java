package org.kiwiproject.dropwizard.util.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kiwiproject.test.jaxrs.JaxrsTestHelper.assertNoContentResponse;

import io.dropwizard.jersey.optional.EmptyOptionalException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("EmptyOptionalNoContentExceptionMapper")
class EmptyOptionalNoContentExceptionMapperTest {

    @Test
    void shouldReturnNoContentResponse() {
        var ex = EmptyOptionalException.INSTANCE;
        var response = new EmptyOptionalNoContentExceptionMapper().toResponse(ex);

        assertNoContentResponse(response);
        assertThat(response.getEntity()).isNull();
    }
}
