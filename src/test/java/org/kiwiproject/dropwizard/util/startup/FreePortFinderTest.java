package org.kiwiproject.dropwizard.util.startup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kiwiproject.test.constants.KiwiTestConstants.OBJECT_MAPPER;

import io.dropwizard.configuration.ResourceConfigurationSourceProvider;
import io.dropwizard.configuration.YamlConfigurationFactory;
import io.dropwizard.core.Configuration;
import io.dropwizard.jackson.DiscoverableSubtypeResolver;
import io.dropwizard.jersey.validation.Validators;
import jakarta.validation.Validator;
import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("FreePortFinder")
class FreePortFinderTest {

    private static final Validator VALIDATOR = Validators.newValidator();

    private static final YamlConfigurationFactory<CustomConfig> CONFIG_FACTORY =
            new YamlConfigurationFactory<>(CustomConfig.class, VALIDATOR, OBJECT_MAPPER, "dw");

    @Test
    void shouldFindDiscoverableFreePortFinderTypes() {
        var subtypes = new DiscoverableSubtypeResolver().getDiscoveredSubtypes();
        assertThat(subtypes).contains(
            AdjacentFreePortFinder.class,
            IncrementingFreePortFinder.class,
            RandomFreePortFinder.class
        );
    }

    @Test
    void shouldUseFieldDefault_FreePortFinder_WhenNotInYaml() {
        var config = parse("none.yml");
        assertThat(config.getFreePortFinder()).isExactlyInstanceOf(SimpleFreePortFinder.class);
    }

    @Test
    void shouldBuildAdjacentFreePortFinder() {
        var config = parse("adjacent.yml");
        assertThat(config.getFreePortFinder()).isExactlyInstanceOf(AdjacentFreePortFinder.class);
    }

    @Test
    void shouldBuildIncrementingFreePortFinder() {
        var config = parse("incrementing.yml");
        assertThat(config.getFreePortFinder()).isExactlyInstanceOf(IncrementingFreePortFinder.class);
    }

    @Test
    void shouldBuildRandomFreePortFinder() {
        var config = parse("random.yml");
        assertThat(config.getFreePortFinder()).isExactlyInstanceOf(RandomFreePortFinder.class);
    }

    private static CustomConfig parse(String configPath) {
        try {
            return CONFIG_FACTORY.build(new ResourceConfigurationSourceProvider(), "FreePortFactoryTest/" + configPath);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Getter
    @Setter
    public static class CustomConfig extends Configuration {
        private String description;
        private FreePortFinder freePortFinder = new SimpleFreePortFinder();
    }

    public static class SimpleFreePortFinder implements FreePortFinder {
        @Override
        public ServicePorts find(AllowablePortRange portRange) {
            return new ServicePorts(portRange.getMinPortNumber(), portRange.getMinPortNumber() + 1);
        }
    }
}
