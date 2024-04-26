package org.kiwiproject.dropwizard.util.bundle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.core.Configuration;
import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.kiwiproject.test.util.Fixtures;
import org.kiwiproject.yaml.YamlHelper;

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
        var portsConfig = new DynamicPortsConfiguration(null, null, null, null, null);
        assertDefaultValues(portsConfig);
    }

    private static void assertDefaultValues(DynamicPortsConfiguration portsConfig) {
        assertAll(
            () -> assertThat(portsConfig.isUseDynamicPorts()).isTrue(),
            () -> assertThat(portsConfig.isUseSecureDynamicPorts()).isTrue(),
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
            () -> assertThat(portsConfig.getMinDynamicPort()).isEqualTo(1_024),
            () -> assertThat(portsConfig.getMaxDynamicPort()).isEqualTo(65_535),
            () -> assertThat(portsConfig.getTlsContextConfiguration()).isNotNull()
        );
    }

    @Test
    void shouldHaveDefaults_WhenDeserializeFromYaml_AndContainsAllProperties_ForDynamicPorts() {
        var configuration = readMyDynamicConfig("DynamicPortsConfigurationTest/dynamic-ports-config-all-values-dynamic.yml");
        var portsConfig = configuration.getDynamicPortsConfiguration();

        assertAll(
            () -> assertThat(portsConfig.isUseDynamicPorts()).isTrue(),
            () -> assertThat(portsConfig.isUseSecureDynamicPorts()).isTrue(),
            () -> assertThat(portsConfig.getMinDynamicPort()).isEqualTo(27_000),
            () -> assertThat(portsConfig.getMaxDynamicPort()).isEqualTo(29_000),
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
            () -> assertThat(portsConfig.getMinDynamicPort()).isEqualTo(27_000),
            () -> assertThat(portsConfig.getMaxDynamicPort()).isEqualTo(29_000),
            () -> assertThat(portsConfig.getTlsContextConfiguration()).isNotNull()
        );
    }

    private static MyDynamicConfig readMyDynamicConfig(String resourceName) {
        var yaml = Fixtures.fixture(resourceName);

        var yamlHelper = new YamlHelper();
        return yamlHelper.toObject(yaml, MyDynamicConfig.class);
    }

    @Getter
    @Setter
    public static class MyDynamicConfig extends Configuration {
        private String name;

        @JsonProperty("dynamicPorts")
        private DynamicPortsConfiguration dynamicPortsConfiguration = new DynamicPortsConfiguration();
    }
}
