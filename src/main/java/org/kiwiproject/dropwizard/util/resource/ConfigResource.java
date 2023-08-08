package org.kiwiproject.dropwizard.util.resource;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.kiwiproject.json.KiwiJacksonSerializers.buildPropertyMaskingSafeSerializerModule;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.dropwizard.core.Configuration;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;
import org.kiwiproject.json.JsonHelper;

import java.util.List;
import java.util.function.Consumer;

/**
 * JAX-RS resource that enables the retrieval of the current runtime configuration of the service.
 * <p>
 * A list of regex patterns can be provided to the "default" constructor in order to mask secrets in the configuration.
 */
@Path("app/config")
public class ConfigResource {

    private final JsonHelper jsonHelper;
    private final Configuration config;

    /**
     * This is the "default" constructor and is the recommended way to create instances. If you
     * have very specific requirements or needs for serialization, you can use one of the constructors
     * that accepts an {@link ObjectMapper} or a {@link JsonHelper}.
     * <p>
     * The created instance contains an {@link ObjectMapper} that disables
     * {@link SerializationFeature#FAIL_ON_EMPTY_BEANS} and which masks properties that contain any of the
     * provided regular expressions in {@code hiddenRegex}.
     *
     * @param config      the Dropwizard {@link Configuration}
     * @param hiddenRegex list of regex patterns for masking secrets in the configuration
     */
    public ConfigResource(Configuration config, List<String> hiddenRegex) {
        this.config = config;
        this.jsonHelper = JsonHelper.newDropwizardJsonHelper();

        jsonHelper.getObjectMapper().registerModule(buildPropertyMaskingSafeSerializerModule(hiddenRegex));
        jsonHelper.getObjectMapper().disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    }

    /**
     * Create an instance using the given {@link ObjectMapper} "as-is". No customizations are done, so
     * make sure it handles everything you need such as masking passwords or secrets.
     *
     * @param config       the Dropwizard {@link Configuration}
     * @param objectMapper the ObjectMapper to use for JSON serialization
     */
    public ConfigResource(Configuration config, ObjectMapper objectMapper) {
        this(config, new JsonHelper(objectMapper));
    }

    /**
     * Create an instance using the given {@link JsonHelper} "as-is". No customizations are done, so make
     * sure it handles everything you need such as masking passwords or secrets.
     *
     * @param config     the Dropwizard {@link Configuration}
     * @param jsonHelper the JsonHelper to use for JSON serialization
     */
    public ConfigResource(Configuration config, JsonHelper jsonHelper) {
        this.config = config;
        this.jsonHelper = jsonHelper;
    }

    /**
     * Create an instance and customize how JSON serialization is performed using the {@link Consumer}.
     * Only the customizations applied in the Consumer are applied to the {@link ObjectMapper}, so make
     * sure to handle everything you need such as masking passwords or secrets.
     *
     * @param config                 the Dropwizard {@link Configuration}
     * @param objectMapperCustomizer a Consumer that provides an ObjectMapper for customization
     */
    public ConfigResource(Configuration config, Consumer<ObjectMapper> objectMapperCustomizer) {
        this.config = config;
        this.jsonHelper = JsonHelper.newDropwizardJsonHelper();

        objectMapperCustomizer.accept(jsonHelper.getObjectMapper());
    }

    @GET
    @Timed
    @ExceptionMetered
    @Produces(APPLICATION_JSON)
    public Response getCurrentConfiguration() {
        var json = jsonHelper.toJson(config);
        return Response.ok(json).build();
    }
}
