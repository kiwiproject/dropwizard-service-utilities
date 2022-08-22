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
     * Registers all the "standard" Jackson configurations on the {@link ObjectMapper} contained in the given
     * Dropwizard {@link Environment} according to the values in the {@link JacksonConfig}.
     * <p>
     * Please note that this <em>modifies</em> the Dropwizard {@link Environment}'s object mapper with our "standard"
     * configurations.
     *
     * @param config      a config model containing various options for configuring Jackson
     * @param environment the Dropwizard environment
     * @return The configured {@link ObjectMapper}, which is the same exact object returned by {@link Environment#getObjectMapper()})
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
     * <p>
     * This method only configures the handler if {@code JacksonConfig#isIgnoreButWarnForUnknownJsonProperties()} is
     * true. If the aforementioned property is true, this method adds the health check if
     * {@code JacksonConfig#isRegisterHealthCheckForUnknownJsonProperties()} is true.
     *
     * @param config       a config model containing various options for configuring Jackson
     * @param mapper       the {@link ObjectMapper} to attach the handler to
     * @param healthChecks the {@link HealthCheckRegistry} to register the health check on
     */
    public static void registerJacksonDeserializationProblemHandler(JacksonConfig config,
                                                                    ObjectMapper mapper,
                                                                    HealthCheckRegistry healthChecks) {

        if (config.isIgnoreButWarnForUnknownJsonProperties()) {
            var handler = new LoggingDeserializationProblemHandler();
            mapper.addHandler(handler);

            if (config.isRegisterHealthCheckForUnknownJsonProperties()) {
                healthChecks.register("Unknown JSON Properties", new UnknownPropertiesHealthCheck(handler));
            }
        }
    }

    /**
     * Optionally configures the {@link ObjectMapper} to convert all Timestamps into milliseconds.
     * <p>
     * This method only configures the mapper's timestamp handling if
     * {@code JacksonConfig#isReadAndWriteDateTimestampsAsMillis()} is true.
     *
     * @param config a config model containing various options for configuring Jackson
     * @param mapper the {@link ObjectMapper} to configure
     */
    public static void registerJacksonTimestampSerialization(JacksonConfig config, ObjectMapper mapper) {
        if (config.isReadAndWriteDateTimestampsAsMillis()) {
            LOG.debug("Configuring Jackson to read/write numeric timestamps as milliseconds");
            JsonHelper.configureForMillisecondDateTimestamps(mapper);
        }
    }

    /**
     * Optionally configures the {@link ObjectMapper} to serialize {@link JAXBElement} elements
     * using {@link JaxbElementSerializer}. Converts nil {@link JAXBElement} into JSON null.
     * <p>
     * This method only configures the mapper if {@code JacksonConfig#isWriteNilJaxbElementsAsNull()} is true.
     *
     * @param config a config model containing various options for configuring Jackson
     * @param mapper the {@link ObjectMapper} to configure
     * @see JaxbElementSerializer
     */
    public static void registerJaxbSerializer(JacksonConfig config, ObjectMapper mapper) {
        if (config.isWriteNilJaxbElementsAsNull()) {
            var jaxbModule = new SimpleModule();
            jaxbModule.addSerializer(JAXBElement.class, new JaxbElementSerializer());
            mapper.registerModule(jaxbModule);
        }
    }
}
