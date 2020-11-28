package org.kiwiproject.dropwizard.util.environment;

import io.dropwizard.setup.Environment;
import lombok.experimental.UtilityClass;
import org.glassfish.jersey.server.ServerProperties;
import org.kiwiproject.validation.KiwiValidations;

import java.util.Map;

/**
 * Set of utilities that assist in setting up the Dropwizard environment
 */
@UtilityClass
public class StandardEnvironmentConfigurations {

    /**
     * Enables the generation of the WADL endpoint in the Dropwizard service
     * <p>
     * NOTE: Only call this if you want the WADL generation, the default in Dropwizard is to have this disabled.
     *
     * @param environment the Dropwizard environment
     */
    public void enableWadlGeneration(Environment environment) {
        Map<String, Object> properties = Map.of(ServerProperties.WADL_FEATURE_DISABLE, false);
        environment.jersey().getResourceConfig().addProperties(properties);
    }

    /**
     * Sets the validator for {@link KiwiValidations} to the validator in the environment.
     *
     * @param environment the Dropwizard environment
     */
    public void configureKiwiValidator(Environment environment) {
        KiwiValidations.setValidator(environment.getValidator());
    }
}
