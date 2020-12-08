package org.kiwiproject.dropwizard.util.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.jetty.io.EofException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("EarlyEofExceptionMapper")
class EarlyEofExceptionMapperTest {

    @Test
    void testMapper() {
        var mapper = new EarlyEofExceptionMapper();
        var response = mapper.toResponse(new EofException("oops"));
        assertThat(response.getStatus()).isEqualTo(400);
    }
}
