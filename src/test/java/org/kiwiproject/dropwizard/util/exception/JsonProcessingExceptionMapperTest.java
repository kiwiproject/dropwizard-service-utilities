package org.kiwiproject.dropwizard.util.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kiwiproject.dropwizard.util.exception.ErrorMessageAssertions.assertAndGetErrorMessage;
import static org.kiwiproject.test.constants.KiwiTestConstants.OBJECT_MAPPER;
import static org.kiwiproject.test.jaxrs.JaxrsTestHelper.assertBadRequest;
import static org.kiwiproject.test.jaxrs.JaxrsTestHelper.assertInternalServerErrorResponse;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException;
import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("JsonProcessingExceptionMapper")
class JsonProcessingExceptionMapperTest {

    @Test
    void shouldExposeDefaultErrorMessage() {
        assertThat(JsonProcessingExceptionMapper.DEFAULT_MSG).isEqualTo(JsonExceptionMappers.DEFAULT_MSG);
    }

    @Test
    void shouldProcessJsonProcessingException() {
        var mapper = new JsonProcessingExceptionMapper();
        var jsonException = createJsonException();
        var response = mapper.toResponse(jsonException);
        assertBadRequest(response);

        var errorMessage = assertAndGetErrorMessage(response);
        assertThat(errorMessage.getMessage()).isEqualTo(jsonException.getOriginalMessage());
    }

    private JsonProcessingException createJsonException() {
        // missing comma before 'last' property
        var badJson = """
            {
                "first": "Bob"
                "last": "Jones"
            }
            """;
        try {
            OBJECT_MAPPER.readValue(badJson, Person.class);
        } catch (JsonProcessingException e) {
            return e;
        }
        throw new RuntimeException("somehow didn't get an exception parsing the bad JSON");
    }

    @Test
    void shouldProcessJsonGenerationException() {
        var mapper = new JsonProcessingExceptionMapper();
        var jsonException = new JsonGenerationException("Problem generating", mock(JsonGenerator.class));
        var response = mapper.toResponse(jsonException);
        assertInternalServerErrorResponse(response);

        var errorMessage = assertAndGetErrorMessage(response);
        assertThat(errorMessage.getMessage()).isEqualTo(jsonException.getOriginalMessage());
    }

    @Test
    void shouldProcessInvalidDefinitionException() {
        var mapper = new JsonProcessingExceptionMapper();
        var jsonException = InvalidDefinitionException.from(mock(JsonParser.class),
                "Problem generating", mock(JavaType.class));

        var response = mapper.toResponse(jsonException);
        assertInternalServerErrorResponse(response);

        var errorMessage = assertAndGetErrorMessage(response);
        assertThat(errorMessage.getMessage()).isEqualTo(jsonException.getOriginalMessage());
    }

    @Test
    void shouldProcessJsonProcessingException_WithSpecificMessage() {
        var mapper = new JsonProcessingExceptionMapper();
        var jsonException = new JsonParseException(mock(JsonParser.class), "No suitable constructor found for Foo");
        var response = mapper.toResponse(jsonException);
        assertInternalServerErrorResponse(response);

        var errorMessage = assertAndGetErrorMessage(response);
        assertThat(errorMessage.getMessage()).isEqualTo(jsonException.getOriginalMessage());
    }

    @Getter
    @Setter
    private static class Person {
        private String first;
        private String last;
    }
}
