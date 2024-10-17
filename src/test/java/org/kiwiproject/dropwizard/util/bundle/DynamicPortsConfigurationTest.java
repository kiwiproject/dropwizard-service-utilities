package org.kiwiproject.dropwizard.util.bundle;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.set;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import io.dropwizard.core.Configuration;
import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.kiwiproject.dropwizard.util.exception.NoAvailablePortException;
import org.kiwiproject.dropwizard.util.startup.AdjacentFreePortFinder;
import org.kiwiproject.dropwizard.util.startup.AllowablePortRange;
import org.kiwiproject.dropwizard.util.startup.FreePortFinder;
import org.kiwiproject.dropwizard.util.startup.IncrementingFreePortFinder;
import org.kiwiproject.dropwizard.util.startup.RandomFreePortFinder;
import org.kiwiproject.net.LocalPortChecker;
import org.kiwiproject.test.util.Fixtures;
import org.kiwiproject.yaml.YamlHelper;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;

@DisplayName("DynamicPortsConfiguration")
class DynamicPortsConfigurationTest {

    @Test
    void shouldHaveDefaults_WithNoArgsConstructor() {
        var portsConfig = new DynamicPortsConfiguration();
        assertDefaultValues(portsConfig);
    }

    @Test
    void shouldHaveDefaults_WithBuilder() {
        var portsConfig = DynamicPortsConfiguration.builder().build();
        assertDefaultValues(portsConfig);
    }

    @Test
    void shouldHaveDefaults_WithAllArgsConstructor() {
        var portsConfig = new DynamicPortsConfiguration(null, null, null, null, null, null);
        assertDefaultValues(portsConfig);
    }

    private static void assertDefaultValues(DynamicPortsConfiguration portsConfig) {
        assertAll(
            () -> assertThat(portsConfig.isUseDynamicPorts()).isTrue(),
            () -> assertThat(portsConfig.isUseSecureDynamicPorts()).isTrue(),
            () -> assertThat(portsConfig.getFreePortFinder()).isExactlyInstanceOf(RandomFreePortFinder.class),
            () -> assertThat(portsConfig.getMinDynamicPort()).isEqualTo(1_024),
            () -> assertThat(portsConfig.getMaxDynamicPort()).isEqualTo(65_535),
            () -> assertThat(portsConfig.getTlsContextConfiguration()).isNull()
        );
    }

    @Test
    void shouldHaveDefaults_WhenDeserializeFromYaml_WhenOnlyTlsConfigurationSpecified() {
        var configuration = readMyDynamicConfig("DynamicPortsConfigurationTest/dynamic-ports-config-only-tls.yml");
        var portsConfig = configuration.getDynamicPortsConfiguration();

        assertAll(
            () -> assertThat(portsConfig.isUseDynamicPorts()).isTrue(),
            () -> assertThat(portsConfig.isUseSecureDynamicPorts()).isTrue(),
            () -> assertThat(portsConfig.getFreePortFinder()).isExactlyInstanceOf(RandomFreePortFinder.class),
            () -> assertThat(portsConfig.getMinDynamicPort()).isEqualTo(1_024),
            () -> assertThat(portsConfig.getMaxDynamicPort()).isEqualTo(65_535),
            () -> assertThat(portsConfig.getTlsContextConfiguration()).isNotNull()
        );
    }

    @Test
    void shouldHaveExpectedValues_WhenDeserializeFromYaml_AndContainsAllProperties_ForDynamicPorts() {
        var configuration = readMyDynamicConfig("DynamicPortsConfigurationTest/dynamic-ports-config-all-values-dynamic.yml");
        var portsConfig = configuration.getDynamicPortsConfiguration();

        assertAll(
            () -> assertThat(portsConfig.isUseDynamicPorts()).isTrue(),
            () -> assertThat(portsConfig.isUseSecureDynamicPorts()).isTrue(),
            () -> assertThat(portsConfig.getFreePortFinder()).isExactlyInstanceOf(AdjacentFreePortFinder.class),
            () -> assertThat(portsConfig.getMinDynamicPort()).isEqualTo(27_000),
            () -> assertThat(portsConfig.getMaxDynamicPort()).isEqualTo(29_000),
            () -> assertThat(portsConfig.getTlsContextConfiguration()).isNotNull()
        );
    }

    @Test
    void shouldHaveExpectedValues_WhenDeserializeFromYaml_WithCustomFreePortFinder_ForDynamicPorts() {
        var configuration = readMyDynamicConfig("DynamicPortsConfigurationTest/dynamic-ports-config-all-values-dynamic-custom-port-finder.yml");
        var portsConfig = configuration.getDynamicPortsConfiguration();

        assertAll(
            () -> assertThat(portsConfig.isUseDynamicPorts()).isTrue(),
            () -> assertThat(portsConfig.isUseSecureDynamicPorts()).isTrue(),
            () -> assertThat(portsConfig.getFreePortFinder())
                    .isExactlyInstanceOf(LowHighFreePortFinder.class)
                    .extracting("bannedPorts", as(set(Integer.class)) )
                    .containsOnly(42, 48),
            () -> assertThat(portsConfig.getMinDynamicPort()).isEqualTo(30),
            () -> assertThat(portsConfig.getMaxDynamicPort()).isEqualTo(50),
            () -> assertThat(portsConfig.getTlsContextConfiguration()).isNotNull()
        );
    }

    @Test
    void shouldHaveDefaults_WhenDeserializeFromYaml_AndContainsAllProperties_ForStaticPorts() {
        var configuration = readMyDynamicConfig("DynamicPortsConfigurationTest/dynamic-ports-config-all-values-not-dynamic.yml");
        var portsConfig = configuration.getDynamicPortsConfiguration();

        assertAll(
            () -> assertThat(portsConfig.isUseDynamicPorts()).isFalse(),
            () -> assertThat(portsConfig.isUseSecureDynamicPorts()).isFalse(),
            () -> assertThat(portsConfig.getFreePortFinder()).isExactlyInstanceOf(RandomFreePortFinder.class),
            () -> assertThat(portsConfig.getMinDynamicPort()).isEqualTo(27_000),
            () -> assertThat(portsConfig.getMaxDynamicPort()).isEqualTo(29_000),
            () -> assertThat(portsConfig.getTlsContextConfiguration()).isNotNull()
        );
    }

    private static MyDynamicConfig readMyDynamicConfig(String resourceName) {
        var yaml = Fixtures.fixture(resourceName);

        var yamlMapper = new YAMLMapper();
        var module = new SimpleModule();
        module.registerSubtypes(
            AdjacentFreePortFinder.class,
            IncrementingFreePortFinder.class,
            RandomFreePortFinder.class,
            LowHighFreePortFinder.class);
        yamlMapper.registerModule(module);

        var yamlHelper = new YamlHelper(yamlMapper);
        return yamlHelper.toObject(yaml, MyDynamicConfig.class);
    }

    @Getter
    @Setter
    public static class MyDynamicConfig extends Configuration {
        private String name;

        @JsonProperty("dynamicPorts")
        private DynamicPortsConfiguration dynamicPortsConfiguration = new DynamicPortsConfiguration();
    }

    @Getter
    @Setter
    @JsonTypeName("lowHigh")
    public static class LowHighFreePortFinder implements FreePortFinder {

        @JsonProperty
        private Set<Integer> bannedPorts = new HashSet<>();

        @Override
        public ServicePorts find(AllowablePortRange portRange) {
            var localPortChecker = new LocalPortChecker();

            var minPort = portRange.getMinPortNumber();
            var applicationPort = IntStream.iterate(minPort, port-> port + 1)
                    .filter(localPortChecker::isPortAvailable)
                    .findFirst()
                    .orElseThrow(() -> new NoAvailablePortException("cannot find an application port"));                    ;

            var maxPort = portRange.getMaxPortNumber();
            var adminPort = IntStream.iterate(maxPort, port -> port - 1)
                    .filter(localPortChecker::isPortAvailable)
                    .findFirst()
                    .orElseThrow(() -> new NoAvailablePortException("cannot find an admin port"));

            return new ServicePorts(applicationPort, adminPort);
        }
    }
}
