package org.kiwiproject.dropwizard.util.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kiwiproject.dropwizard.util.exception.ErrorMessageAssertions.assertAndGetErrorMessage;
import static org.kiwiproject.test.constants.KiwiTestConstants.JSON_HELPER;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.kiwiproject.json.RuntimeJsonException;

import java.io.IOException;

@DisplayName("RuntimeJsonExceptionMapper")
class RuntimeJsonExceptionMapperTest {

    private RuntimeJsonExceptionMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new RuntimeJsonExceptionMapper();
    }

    @Test
    void shouldExposeDefaultErrorMessage() {
        assertThat(RuntimeJsonExceptionMapper.DEFAULT_MSG).isEqualTo(JsonExceptionMappers.DEFAULT_MSG);
    }

    @Test
    void shouldProcessExceptionHavingCauseOfJsonProcessingException() {
        var runtimeJsonException = createRuntimeJsonException();
        var jsonException = (JsonProcessingException) runtimeJsonException.getCause();
        var response = mapper.toResponse(runtimeJsonException);
        assertBadRequest(response);

        var errorMessage = assertAndGetErrorMessage(response);
        assertThat(errorMessage.getMessage()).isEqualTo(jsonException.getOriginalMessage());
    }

    private static RuntimeJsonException createRuntimeJsonException() {
        // missing comma before 'last' property
        var badJson = """
            {
                "first": "Bob"
                "last": "Jones"
            }
            """;
        try {
            JSON_HELPER.toObject(badJson, Person.class);
        } catch (RuntimeJsonException e) {
            return e;
        }
        throw new RuntimeException("somehow didn't get a RuntimeJsonException parsing the bad JSON");
    }

    @Test
    void shouldProcessExceptionHavingCauseOfJsonGenerationException() {
        var jsonException = new JsonGenerationException("Problem generating", mock(JsonGenerator.class));
        var runtimeJsonException = new RuntimeJsonException(jsonException);
        var response = mapper.toResponse(runtimeJsonException);
        assertInternalServerErrorResponse(response);

        var errorMessage = assertAndGetErrorMessage(response);
        assertThat(errorMessage.getMessage()).isEqualTo(jsonException.getOriginalMessage());
    }

    @Test
    void shouldProcessExceptionHavingCauseOfInvalidDefinitionException() {
        var jsonException = InvalidDefinitionException.from(mock(JsonParser.class),
                "Problem generating", mock(JavaType.class));
        var runtimeJsonException = new RuntimeJsonException(jsonException);

        var response = mapper.toResponse(runtimeJsonException);
        assertInternalServerErrorResponse(response);

        var errorMessage = assertAndGetErrorMessage(response);
        assertThat(errorMessage.getMessage()).isEqualTo(jsonException.getOriginalMessage());
    }

    @Test
    void shouldProcessExceptionHavingCauseOfJsonProcessingException_WithSpecificMessage() {
        var jsonException = new JsonParseException(mock(JsonParser.class), "No suitable constructor found for Foo");
        var runtimeJsonException = new RuntimeJsonException(jsonException);

        var response = mapper.toResponse(runtimeJsonException);
        assertInternalServerErrorResponse(response);

        var errorMessage = assertAndGetErrorMessage(response);
        assertThat(errorMessage.getMessage()).isEqualTo(jsonException.getOriginalMessage());
    }

    @Test
    void shouldProcessExceptionHavingCauseOfUnsupportedType() {
        var ioException = new IOException("some weird character encoding problem...");
        var runtimeJsonException = new RuntimeJsonException(ioException);
        var response = mapper.toResponse(runtimeJsonException);
        assertInternalServerErrorResponse(response);

        var errorMessage = assertAndGetErrorMessage(response);
        assertThat(errorMessage.getMessage()).isEqualTo(ioException.getMessage());
    }

    @Getter
    @Setter
    private static class Person {
        private String first;
        private String last;
    }
}
