package org.kiwiproject.dropwizard.util.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kiwiproject.test.constants.KiwiTestConstants.OBJECT_MAPPER;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.kiwiproject.jaxrs.exception.ErrorMessage;

import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

@DisplayName("JsonProcessingExceptionMapper")
class JsonProcessingExceptionMapperTest {

    @Test
    void shouldProcessJsonProcessingException() {
        var mapper = new JsonProcessingExceptionMapper();
        var jsonException = createJsonException();
        var response = mapper.toResponse(jsonException);
        var errorMessage = getErrorMessage(response);

        assertThat(errorMessage.getMessage()).isEqualTo(jsonException.getOriginalMessage());
    }

    private JsonProcessingException createJsonException() {
        String badJson = "{ \"first\": \"Bob\" \"last\": \"Jones\" )"; // missing comma before 'last property
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
        var errorMessage = getErrorMessage(response);

        assertThat(errorMessage.getMessage()).isEqualTo(jsonException.getOriginalMessage());
    }

    @Test
    void shouldProcessJsonProcessingException_WithSpecificMessage() {
        var mapper = new JsonProcessingExceptionMapper();
        var jsonException = new JsonParseException(mock(JsonParser.class), "No suitable constructor found for Foo");
        var response = mapper.toResponse(jsonException);
        var errorMessage = getErrorMessage(response);

        assertThat(errorMessage.getMessage()).isEqualTo(jsonException.getOriginalMessage());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ErrorMessage getErrorMessage(Response r) {
        Map entity = (Map) r.getEntity();
        List<ErrorMessage> errors = (List<ErrorMessage>) entity.get("errors");
        return errors.get(0);
    }

    @Getter
    @Setter
    private static class Person {
        private String first;
        private String last;
    }
}
