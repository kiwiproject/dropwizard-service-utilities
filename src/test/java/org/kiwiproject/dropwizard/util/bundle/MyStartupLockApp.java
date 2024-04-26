package org.kiwiproject.dropwizard.util.bundle;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.dropwizard.core.Application;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;
import io.dropwizard.util.Duration;
import lombok.Getter;
import org.kiwiproject.curator.config.CuratorConfig;
import org.kiwiproject.dropwizard.util.startup.ExecutionStrategies;
import org.kiwiproject.dropwizard.util.startup.PortAssigner;
import org.kiwiproject.dropwizard.util.startup.StartupLockInfo;
import org.kiwiproject.dropwizard.util.startup.StartupLocker;
import org.kiwiproject.dropwizard.util.startup.SystemExecutioner;

public class MyStartupLockApp extends Application<MyStartupLockConfig> {

    @Getter
    final ExecutionStrategies.ExitFlaggingExecutionStrategy executionStrategy =
            (ExecutionStrategies.ExitFlaggingExecutionStrategy) ExecutionStrategies.exitFlagging();

    @Getter
    final SystemExecutioner systemExecutioner = new SystemExecutioner(executionStrategy);

    @Getter
    StartupLocker startupLocker;

    public MyStartupLockApp() {
        startupLocker = mock(StartupLocker.class);

        when(startupLocker.acquireStartupLock(
                anyString(),
                any(Duration.class),
                any(PortAssigner.PortAssignment.class),
                any(CuratorConfig.class),
                any(Environment.class)))
                .thenReturn(mock(StartupLockInfo.class));
    }

    @Override
    public void initialize(Bootstrap<MyStartupLockConfig> bootstrap) {
        var startupLockBundle = new StartupLockBundle<MyStartupLockConfig>() {
            @Override
            public StartupLockConfiguration getStartupLockConfiguration(MyStartupLockConfig configuration) {
                return StartupLockConfiguration.builder()
                        .useDynamicPorts(configuration.isUseDynamicPorts())
                        .zkStartupLockPath(configuration.getZkStartupLockPath())
                        .build();
            }

            @Override
            public SystemExecutioner getExecutioner() {
                return systemExecutioner;
            }

            @Override
            StartupLocker buildStartupLocker() {
                return startupLocker;
            }
        };

        bootstrap.addBundle(startupLockBundle);
    }

    @Override
    public void run(MyStartupLockConfig configuration, Environment environment) {
    }
}
