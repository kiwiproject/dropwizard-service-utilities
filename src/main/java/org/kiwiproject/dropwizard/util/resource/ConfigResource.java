package org.kiwiproject.dropwizard.util.resource;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.kiwiproject.json.KiwiJacksonSerializers.buildPropertyMaskingSafeSerializerModule;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import io.dropwizard.Configuration;
import org.kiwiproject.json.JsonHelper;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import java.util.List;

/**
 * JAX-RS resource that enables the retrieval of the current runtime configuration of the service.
 * <p>
 * A list of regex patterns can be given in order to mask secrets in the configuration.
 */
@Path("app/config")
public class ConfigResource {

    // Intentionally creating a separate JsonHelper to allow customizations
    private final JsonHelper jsonHelper;
    private final Configuration config;

    public ConfigResource(Configuration config, List<String> hiddenRegex) {
        this.config = config;
        this.jsonHelper = JsonHelper.newDropwizardJsonHelper();

        jsonHelper.getObjectMapper().registerModule(buildPropertyMaskingSafeSerializerModule(hiddenRegex));
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
