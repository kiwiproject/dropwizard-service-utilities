package org.kiwiproject.dropwizard.util.jackson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.codahale.metrics.health.HealthCheckRegistry;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.Getter;
import lombok.Setter;
import lombok.Value;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.kiwiproject.dropwizard.util.config.JacksonConfig;
import org.kiwiproject.dropwizard.util.health.UnknownPropertiesHealthCheck;
import org.kiwiproject.json.JsonHelper;
import org.kiwiproject.json.LoggingDeserializationProblemHandler;
import org.kiwiproject.test.dropwizard.mockito.DropwizardMockitoMocks;

import javax.validation.Validator;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.namespace.QName;
import java.time.Instant;
import java.util.Date;

@DisplayName("StandardJacksonConfigurations")
class StandardJacksonConfigurationsTest {

    @Nested
    class RegisterJaxbSerializer {

        @Test
        void shouldRegisterModule_WhenConfigured() {
            var config = JacksonConfig.builder().build();
            var mapper = mock(ObjectMapper.class);

            StandardJacksonConfigurations.registerJaxbSerializer(config, mapper);

            verify(mapper).registerModule(isA(SimpleModule.class));
        }

        @Test
        void shouldConvertNilJAXBElements_ToNull() {
            var config = new JacksonConfig();
            var mapper = new ObjectMapper();

            StandardJacksonConfigurations.registerJaxbSerializer(config, mapper);

            var testXmlBean = newTestXmlBean(null, null);
            var jsonHelper = new JsonHelper(mapper);
            var json = jsonHelper.toJson(testXmlBean);

            assertThat(json)
                    .containsIgnoringWhitespaces("\"wrappedString\":null")
                    .containsIgnoringWhitespaces("\"wrappedInteger\":null");

            var map = jsonHelper.toMap(json);
            assertThat(map)
                    .containsEntry("wrappedString", null)
                    .containsEntry("wrappedInteger", null);
        }

        @Test
        void shouldConvertNonNilJAXBElements_ToTheJAXBElementGenericTypes() {
            var config = new JacksonConfig();
            var mapper = new ObjectMapper();

            StandardJacksonConfigurations.registerJaxbSerializer(config, mapper);

            var testXmlBean = newTestXmlBean("The Answer", 42);
            var jsonHelper = new JsonHelper(mapper);
            var json = jsonHelper.toJson(testXmlBean);

            assertThat(json)
                    .containsIgnoringWhitespaces("\"wrappedString\":\"The Answer\"")
                    .containsIgnoringWhitespaces("\"wrappedInteger\":42");

            var map = jsonHelper.toMap(json);
            assertThat(map)
                    .containsEntry("wrappedString", "The Answer")
                    .containsEntry("wrappedInteger", 42);
        }

        @Test
        void shouldNotRegisterModule_WhenNotConfigured() {
            var config = JacksonConfig.builder().writeNilJaxbElementsAsNull(false).build();
            var mapper = mock(ObjectMapper.class);

            StandardJacksonConfigurations.registerJaxbSerializer(config, mapper);

            verifyNoInteractions(mapper);
        }
    }

    @Nested
    class RegisterJacksonTimestampSerialization {

        @Test
        void shouldConfigureMapper_WhenConfigured() {
            var config = JacksonConfig.builder().build();
            var mapper = mock(ObjectMapper.class);

            StandardJacksonConfigurations.registerJacksonTimestampSerialization(config, mapper);

            verify(mapper).configure(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS, false);
            verify(mapper).configure(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS, false);
        }

        @Test
        void shouldMapTimestampsAsEpochMilliseconds() {
            var config = new JacksonConfig();
            var mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());

            StandardJacksonConfigurations.registerJacksonTimestampSerialization(config, mapper);

            var nowMillis = System.currentTimeMillis();
            var testBean = new TestTimestampBean(Instant.ofEpochMilli(nowMillis), new Date(nowMillis));
            var jsonHelper = new JsonHelper(mapper);
            var json = jsonHelper.toJson(testBean);

            var map = jsonHelper.toMap(json);
            assertThat(map)
                    .describedAs("The JSON converted to a Map should contain the epochMillis")
                    .containsEntry("theInstant", nowMillis)
                    .containsEntry("theDate", nowMillis);

            var deserializedTestBean = jsonHelper.toObject(json, TestTimestampBean.class);
            assertThat(deserializedTestBean).isEqualTo(testBean);
        }

        @Test
        void shouldNotConfigureMapper_WhenNotConfigured() {
            var config = JacksonConfig.builder().readAndWriteDateTimestampsAsMillis(false).build();
            var mapper = mock(ObjectMapper.class);

            StandardJacksonConfigurations.registerJacksonTimestampSerialization(config, mapper);

            verifyNoInteractions(mapper);
        }
    }

    @Nested
    class RegisterJacksonDeserializationProblemHandler {

        @Test
        void shouldAddHandler_WhenConfigured() {
            var config = JacksonConfig.builder().registerHealthCheckForUnknownJsonProperties(false).build();
            var mapper = mock(ObjectMapper.class);
            var registry = mock(HealthCheckRegistry.class);

            StandardJacksonConfigurations.registerJacksonDeserializationProblemHandler(config, mapper, registry);

            verify(mapper).addHandler(isA(LoggingDeserializationProblemHandler.class));
            verifyNoInteractions(registry);
        }

        @Test
        void shouldAddHandler_AndRegisterHealthCheck_WhenConfigured() {
            var config = JacksonConfig.builder().build();
            var mapper = mock(ObjectMapper.class);
            var registry = mock(HealthCheckRegistry.class);

            StandardJacksonConfigurations.registerJacksonDeserializationProblemHandler(config, mapper, registry);

            verify(mapper).addHandler(isA(LoggingDeserializationProblemHandler.class));
            verify(registry).register(eq("Unknown JSON Properties"), isA(UnknownPropertiesHealthCheck.class));
        }

        @Test
        void shouldNotAddHandlerOrHealthCheck_WhenNotConfigured() {
            var config = JacksonConfig.builder().ignoreButWarnForUnknownJsonProperties(false).registerHealthCheckForUnknownJsonProperties(false).build();
            var mapper = mock(ObjectMapper.class);
            var registry = mock(HealthCheckRegistry.class);

            StandardJacksonConfigurations.registerJacksonDeserializationProblemHandler(config, mapper, registry);

            verifyNoInteractions(mapper);
            verifyNoInteractions(registry);
        }
    }

    @Nested
    class RegisterAllStandardJacksonConfigurations {

        @Test
        void shouldRegisterAllOfTheStandardConfigurations() {
            var config = JacksonConfig.builder().build();
            var dropwizardMapper = mock(ObjectMapper.class);
            var env = DropwizardMockitoMocks.mockEnvironment(dropwizardMapper, mock(Validator.class));

            var objectMapper = StandardJacksonConfigurations.registerAllStandardJacksonConfigurations(config, env);
            assertThat(objectMapper).isSameAs(dropwizardMapper);

            verify(dropwizardMapper).registerModule(isA(SimpleModule.class));
            verify(dropwizardMapper).configure(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS, false);
            verify(dropwizardMapper).configure(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS, false);
            verify(dropwizardMapper).addHandler(isA(LoggingDeserializationProblemHandler.class));
            verify(env.healthChecks()).register(eq("Unknown JSON Properties"), isA(UnknownPropertiesHealthCheck.class));
        }
    }

    @Value
    public static class TestTimestampBean {
        Instant theInstant;
        Date theDate;
    }

    @Getter
    @Setter
    @XmlRootElement
    private static class TestXmlBean {

        @XmlElement
        private String stringField;

        @XmlElement
        private Integer integerField;

        @XmlElementRef(required = false)
        private JAXBElement<String> wrappedString;

        @XmlElementRef(required = false)
        private JAXBElement<Integer> wrappedInteger;
    }

    private static TestXmlBean newTestXmlBean(String wrappedStringValue, Integer wrappedIntegerValue) {
        var obj = new TestXmlBean();
        obj.setStringField("test");
        obj.setIntegerField(1);
        obj.setWrappedString(new JAXBElement<>(new QName(""), String.class, wrappedStringValue));
        obj.setWrappedInteger(new JAXBElement<>(new QName(""), Integer.class, wrappedIntegerValue));
        return obj;
    }
}
