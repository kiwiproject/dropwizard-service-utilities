package org.kiwiproject.dropwizard.util.startup;

import static org.assertj.core.api.Assertions.assertThat;

import io.dropwizard.core.Configuration;
import io.dropwizard.jackson.DiscoverableSubtypeResolver;
import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.kiwiproject.test.dropwizard.configuration.DropwizardConfigurations;

@DisplayName("FreePortFinder")
class FreePortFinderTest {

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
        return DropwizardConfigurations.newConfiguration(
                CustomConfig.class, "FreePortFactoryTest/" + configPath);
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
