package org.kiwiproject.dropwizard.util.bundle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.dropwizard.testing.junit5.DropwizardAppExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.util.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kiwiproject.curator.config.CuratorConfig;
import org.kiwiproject.dropwizard.util.startup.PortAssigner;
import org.kiwiproject.dropwizard.util.startup.StartupLockInfo;
import org.kiwiproject.dropwizard.util.startup.SystemExecutioner;
import org.kiwiproject.dropwizard.util.startup.ExecutionStrategies.SystemExitExecutionStrategy;
import org.mockito.ArgumentCaptor;

import java.net.InetAddress;
import java.net.UnknownHostException;

@DisplayName("StartupLockBundle")
@ExtendWith(DropwizardExtensionsSupport.class)
class StartupLockBundleTest {

    static final DropwizardAppExtension<MyStartupLockConfig> APP =
            new DropwizardAppExtension<>(MyStartupLockApp.class);

    @Test
    void shouldLockAndUnlock() throws UnknownHostException {
        var application = (MyStartupLockApp) APP.getApplication();
        var startupLockerMock = application.getStartupLocker();

        var expectedLockPath = "/kiwi/service/startup-locks/" + InetAddress.getLocalHost().getHostAddress();

        var inOrder = inOrder(startupLockerMock);

        inOrder.verify(startupLockerMock).acquireStartupLock(
            eq(expectedLockPath),
            eq(Duration.minutes(1)),
            eq(PortAssigner.PortAssignment.DYNAMIC),
            any(CuratorConfig.class),
            same(APP.getEnvironment())
        );

        var startupLockInfoCaptor1 = ArgumentCaptor.forClass(StartupLockInfo.class);
        inOrder.verify(startupLockerMock)
                .addFallbackJettyStartupLifeCycleListener(startupLockInfoCaptor1.capture(), same(APP.getEnvironment()));

        var startupLockInfoCaptor2 = ArgumentCaptor.forClass(StartupLockInfo.class);
        inOrder.verify(startupLockerMock)
                .releaseStartupLockIfPresent(startupLockInfoCaptor2.capture());

        assertThat(startupLockInfoCaptor1.getValue())
                .describedAs("same StartupLockInfo should be used in fallback listenr and release")
                .isSameAs(startupLockInfoCaptor2.getValue());

        verifyNoMoreInteractions(startupLockerMock);
    }

    @Test
    void shouldBuildStartupLocker() {
        var startupLockBundle = new StartupLockBundle<MyStartupLockConfig>() {
            @Override
            public StartupLockConfiguration getStartupLockConfiguration(MyStartupLockConfig configuration) {
                return StartupLockConfiguration.builder()
                        .useDynamicPorts(configuration.isUseDynamicPorts())
                        .zkStartupLockPath(configuration.getZkStartupLockPath())
                        .build();
            }
        };

        var startupLocker = startupLockBundle.buildStartupLocker();
        assertThat(startupLocker).isNotNull();
    }

    @Test
    void shouldProvideDefaultSystemExecutioner() {
        var startupLockBundle = new StartupLockBundle<MyStartupLockConfig>() {
            @Override
            public StartupLockConfiguration getStartupLockConfiguration(MyStartupLockConfig configuration) {
                return StartupLockConfiguration.builder()
                        .useDynamicPorts(configuration.isUseDynamicPorts())
                        .zkStartupLockPath(configuration.getZkStartupLockPath())
                        .build();
            }
        };

        assertThat(startupLockBundle.getExecutioner())
                .isInstanceOf(SystemExecutioner.class)
                .extracting(SystemExecutioner::getExecutionStrategy)
                .isInstanceOf(SystemExitExecutionStrategy.class);
    }
}
