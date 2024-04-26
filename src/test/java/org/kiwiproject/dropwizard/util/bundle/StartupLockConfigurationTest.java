package org.kiwiproject.dropwizard.util.bundle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.core.Configuration;
import io.dropwizard.util.Duration;
import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.kiwiproject.test.util.Fixtures;
import org.kiwiproject.yaml.YamlHelper;

@DisplayName("StartupLockConfiguration")
class StartupLockConfigurationTest {

    @Test
    void shouldHaveDefaults_WithNoArgsConstructor() {
        var startupLockConfig = new StartupLockConfiguration();
        assertDefaultValues(startupLockConfig);
    }

    @Test
    void shouldHaveDefaults_WithBuilder() {
        var startupLockConfig = StartupLockConfiguration.builder().build();
        assertDefaultValues(startupLockConfig);
    }

    @Test
    void shouldHaveDefaults_WithAllArgsConstructor() {
        var startupLockConfig = new StartupLockConfiguration(null, null, null, null);
        assertDefaultValues(startupLockConfig);
    }

    @Test
    void shouldHaveDefaults_WhenDeserializeFromYaml_WhenNoLockConfigurationSpecified() {
        var configuration = readMyConfig("StartupLockConfigurationTest/startup-lock-config-none.yml");

        var startupLockConfig = configuration.getStartupLockConfiguration();
        assertDefaultValues(startupLockConfig);
    }

    private static void assertDefaultValues(StartupLockConfiguration startupLockConfig) {
        assertAll(
            () -> assertThat(startupLockConfig.isUseDynamicPorts()).isTrue(),
            () -> assertThat(startupLockConfig.getZkStartupLockPath()).isEqualTo("/kiwi-startup-locks"),
            () -> assertThat(startupLockConfig.getZkStartupLockTimeout()).isEqualTo(Duration.minutes(1)),
            () -> assertThat(startupLockConfig.getCuratorConfig()).isNotNull()
        );
    }

    @Test
    void shouldAcceptSomeValues_WhenDeserializeFromYaml() {
        var configuration = readMyConfig("StartupLockConfigurationTest/startup-lock-config-some-properties.yml");

        var startupLockConfig = configuration.getStartupLockConfiguration();
        assertAll(
            () -> assertThat(startupLockConfig.isUseDynamicPorts()).isTrue(),
            () -> assertThat(startupLockConfig.getZkStartupLockPath()).isEqualTo("/service/startup-locks"),
            () -> assertThat(startupLockConfig.getZkStartupLockTimeout()).isEqualTo(Duration.seconds(5)),
            () -> assertThat(startupLockConfig.getCuratorConfig()).isNotNull()
        );
    }

    @Test
    void shouldAcceptAllValues_WhenDeserializeFromYaml() {
        var configuration = readMyConfig("StartupLockConfigurationTest/startup-lock-config-all-properties.yml");

        var startupLockConfig = configuration.getStartupLockConfiguration();
        assertAll(
            () -> assertThat(startupLockConfig.isUseDynamicPorts()).isFalse(),
            () -> assertThat(startupLockConfig.getZkStartupLockPath()).isEqualTo("/service/startup/locks"),
            () -> assertThat(startupLockConfig.getZkStartupLockTimeout()).isEqualTo(Duration.seconds(45)),
            () -> assertThat(startupLockConfig.getCuratorConfig())
                    .isNotNull()
                    .extracting("zkConnectString", "sessionTimeout")
                    .containsExactly(
                        "zk1.test.acme.com:2181,zk2.test.acme.com:2181,zk3.test.acme.com:2181",
                        Duration.seconds(30)
                    )
        );
    }

    private static MyConfig readMyConfig(String resourceName) {
        var yaml = Fixtures.fixture(resourceName);

        var yamlHelper = new YamlHelper();
        return yamlHelper.toObject(yaml, MyConfig.class);
    }

    @Getter
    @Setter
    public static class MyConfig extends Configuration {
        private String name;

        @JsonProperty("startupLock")
        private StartupLockConfiguration startupLockConfiguration = new StartupLockConfiguration();
    }
}
