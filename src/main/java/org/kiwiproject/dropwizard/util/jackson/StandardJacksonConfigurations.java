package org.kiwiproject.dropwizard.util.jackson;

import com.codahale.metrics.health.HealthCheckRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.dropwizard.setup.Environment;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.kiwiproject.dropwizard.util.config.JacksonConfig;
import org.kiwiproject.dropwizard.util.health.UnknownPropertiesHealthCheck;
import org.kiwiproject.json.JaxbElementSerializer;
import org.kiwiproject.json.JsonHelper;
import org.kiwiproject.json.LoggingDeserializationProblemHandler;

import javax.xml.bind.JAXBElement;

/**
 * Set of opinionated "standard" utilities to configure Jackson.
 */
@UtilityClass
@Slf4j
public class StandardJacksonConfigurations {

    /**
     * Registers all of the "standard" Jackson configurations.
     *
     * @param config        a config model containing various options for configuring Jackson
     * @param environment   the Dropwizard environment
     * @return The configured {@link ObjectMapper}
     * @see StandardJacksonConfigurations#registerJacksonDeserializationProblemHandler(JacksonConfig, ObjectMapper, HealthCheckRegistry)
     * @see StandardJacksonConfigurations#registerJacksonTimestampSerialization(JacksonConfig, ObjectMapper)
     * @see StandardJacksonConfigurations#registerJaxbSerializer(JacksonConfig, ObjectMapper)
     */
    public static ObjectMapper registerAllStandardJacksonConfigurations(JacksonConfig config, Environment environment) {
        var mapper = environment.getObjectMapper();

        registerJacksonDeserializationProblemHandler(config, mapper, environment.healthChecks());
        registerJacksonTimestampSerialization(config, mapper);
        registerJaxbSerializer(config, mapper);

        return mapper;
    }

    /**
     * Optionally configures a {@link LoggingDeserializationProblemHandler} to log Jackson deserialization issues. This
     * method will also optionally register a Health Check to mark the service unhealthy if there are deserialization
     * issues.
     *
     * @param config        a config model containing various options for configuring Jackson
     * @param mapper        the {@link ObjectMapper} to attach the handler to
     * @param healthChecks  the {@link HealthCheckRegistry} to register the health check on
     */
    public static void registerJacksonDeserializationProblemHandler(JacksonConfig config,
                                                                     ObjectMapper mapper,
                                                                     HealthCheckRegistry healthChecks) {

        if (config.isIgnoreButWarnForUnknownJsonProperties()) {
            var handler = new LoggingDeserializationProblemHandler();
            mapper.addHandler(handler);

            if (config.isRegisterHealthCheckForUnknownJsonProperties()) {
                healthChecks.register("Message Deserialization", new UnknownPropertiesHealthCheck(handler));
            }
        }
    }

    /**
     * Optionally configures the {@link ObjectMapper} to convert all Timestamps into milliseconds.
     *
     * @param config        a config model containing various options for configuring Jackson
     * @param mapper        the {@link ObjectMapper} to configure
     */
    public static void registerJacksonTimestampSerialization(JacksonConfig config, ObjectMapper mapper) {
        if (config.isReadAndWriteDateTimestampsAsMillis()) {
            LOG.debug("Configuring Jackson to read/write numeric timestamps as milliseconds");
            JsonHelper.configureForMillisecondDateTimestamps(mapper);
        }
    }

    /**
     * Optionally configures the {@link ObjectMapper} to convert all nil {@link JAXBElement} into null.
     *
     * @param config        a config model containing various options for configuring Jackson
     * @param mapper        the {@link ObjectMapper} to configure
     */
    public static void registerJaxbSerializer(JacksonConfig config, ObjectMapper mapper) {
        if (config.isWriteNilJaxbElementsAsNull()) {
            var jaxbModule = new SimpleModule();
            jaxbModule.addSerializer(JAXBElement.class, new JaxbElementSerializer());
            mapper.registerModule(jaxbModule);
        }
    }
}