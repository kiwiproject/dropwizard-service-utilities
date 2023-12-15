package org.kiwiproject.dropwizard.util.environment;

import com.google.common.annotations.Beta;
import io.dropwizard.core.setup.Environment;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.jersey.CommonProperties;
import org.glassfish.jersey.server.ServerProperties;
import org.kiwiproject.validation.KiwiValidations;

import java.util.Map;

/**
 * Set of utilities that assist in setting up the Dropwizard environment
 */
@UtilityClass
@Slf4j
public class StandardEnvironmentConfigurations {

    /**
     * An enum that describes whether a Jersey feature is enabled or disabled.
     *
     * @see CommonProperties
     * @see ServerProperties
     * @see org.glassfish.jersey.client.ClientProperties ClientProperties
     */
    @Beta
    public enum JerseyFeatureStatus {
        DISABLED, ENABLED
    }

    /**
     * Enables the generation of the WADL endpoint in the Dropwizard service
     * <p>
     * NOTE: Only call this if you want the WADL generation, the default in Dropwizard is to have this disabled.
     *
     * @param environment the Dropwizard environment
     * @see ServerProperties#WADL_FEATURE_DISABLE
     */
    public static void enableWadlGeneration(Environment environment) {
        Map<String, Object> properties = Map.of(ServerProperties.WADL_FEATURE_DISABLE, false);
        environment.jersey().getResourceConfig().addProperties(properties);
    }

    /**
     * Disables auto-discovery of Jersey {@link jakarta.ws.rs.core.Feature Feature}s
     * <em>globally</em> on both client and server.
     * <p>
     * By default, Jersey feature auto-discovery is automatically enabled.
     * <p>
     * For more information, see the section in the Jersey User Guide on
     * <em>Auto-Discoverable Features</em> in the <em>Application Deployment and Runtime Environments</em>
     * chapter.
     *
     * @param environment the Dropwizard environment
     * @see CommonProperties#FEATURE_AUTO_DISCOVERY_DISABLE
     */
    @Beta
    public static void disableJerseyFeatureAutoDiscovery(Environment environment) {
        jerseyFeatureAutoDiscovery(environment, JerseyFeatureStatus.DISABLED);
    }

    /**
     * Sets auto-discovery of Jersey {@link jakarta.ws.rs.core.Feature Feature}s
     * <em>globally</em> for both client and server to the given {@code featureStatus}.
     * <p>
     * By default, Jersey feature auto-discovery is automatically enabled.
     * <p>
     * For more information, see the section in the Jersey User Guide on
     * <em>Auto-Discoverable Features</em> in the <em>Application Deployment and Runtime Environments</em>
     * chapter.
     *
     * @param environment   the Dropwizard environment
     * @param featureStatus the Jersey feature status
     * @see CommonProperties#FEATURE_AUTO_DISCOVERY_DISABLE
     */
    @Beta
    public static void jerseyFeatureAutoDiscovery(Environment environment, JerseyFeatureStatus featureStatus) {
        var disable = (featureStatus == JerseyFeatureStatus.DISABLED);

        if (disable) {
            LOG.warn("Disabling Jersey feature auto-discovery globally on client and server." +
                    " If there are components that depend on feature auto-discovery, they will NOT work!" +
                    " One common component that uses feature auto-discovery is jersey-media-json-jackson," +
                    " which uses it to register JacksonFeature, but which can cause conflicts such as with" +
                    " Dropwizard's own JacksonFeature. See the 'Auto-Discoverable Features' section in the" +
                    " Jersey user guide for more information.");
        }

        environment.jersey().getResourceConfig()
                .property(CommonProperties.FEATURE_AUTO_DISCOVERY_DISABLE, disable);
    }

    /**
     * Sets the validator for {@link KiwiValidations} to the validator in the environment.
     *
     * @param environment the Dropwizard environment
     */
    public static void configureKiwiValidator(Environment environment) {
        KiwiValidations.setValidator(environment.getValidator());
    }
}
