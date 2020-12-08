package org.kiwiproject.dropwizard.util.jackson;

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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.kiwiproject.dropwizard.util.config.JacksonConfig;
import org.kiwiproject.dropwizard.util.health.UnknownPropertiesHealthCheck;
import org.kiwiproject.json.LoggingDeserializationProblemHandler;
import org.kiwiproject.test.dropwizard.mockito.DropwizardMockitoMocks;

import javax.validation.Validator;

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
            verify(registry).register(eq("Message Deserialization"), isA(UnknownPropertiesHealthCheck.class));
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
            var mapper = mock(ObjectMapper.class);
            var env = DropwizardMockitoMocks.mockEnvironment(mapper, mock(Validator.class));

            StandardJacksonConfigurations.registerAllStandardJacksonConfigurations(config, env);

            verify(mapper).registerModule(isA(SimpleModule.class));
            verify(mapper).configure(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS, false);
            verify(mapper).configure(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS, false);
            verify(mapper).addHandler(isA(LoggingDeserializationProblemHandler.class));
            verify(env.healthChecks()).register(eq("Message Deserialization"), isA(UnknownPropertiesHealthCheck.class));
        }
    }
}
