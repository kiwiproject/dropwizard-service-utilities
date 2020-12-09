package org.kiwiproject.dropwizard.util.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kiwiproject.dropwizard.util.exception.ErrorMessageAssertions.assertAndGetErrorMessage;
import static org.kiwiproject.test.jaxrs.JaxrsTestHelper.assertBadRequest;

import org.eclipse.jetty.io.EofException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("EarlyEofExceptionMapper")
class EarlyEofExceptionMapperTest {

    @Test
    void testMapper() {
        var mapper = new EarlyEofExceptionMapper();
        var response = mapper.toResponse(new EofException("oops"));

        assertBadRequest(response);

        var errorMessage = assertAndGetErrorMessage(response);
        assertThat(errorMessage.getMessage()).isEqualTo("oops");
    }
}
