package org.kiwiproject.dropwizard.util.bundle;

import static org.kiwiproject.base.KiwiPreconditions.requireNotNull;
import static org.kiwiproject.dropwizard.util.bundle.PortAssigners.portAssignmentFrom;

import com.google.common.annotations.VisibleForTesting;
import io.dropwizard.core.Configuration;
import io.dropwizard.core.ConfiguredBundle;
import io.dropwizard.core.setup.Environment;
import lombok.extern.slf4j.Slf4j;
import org.kiwiproject.curator.CuratorFrameworkHelper;
import org.kiwiproject.curator.CuratorLockHelper;
import org.kiwiproject.curator.zookeeper.ZooKeeperAvailabilityChecker;
import org.kiwiproject.dropwizard.util.startup.PortAssigner;
import org.kiwiproject.dropwizard.util.startup.StartupLocker;
import org.kiwiproject.dropwizard.util.startup.SystemExecutioner;

import java.net.InetAddress;
import java.nio.file.Paths;

/**
 * Dropwizard bundle that acquires a distributed ZooKeeper lock during application initialization.
 * <p>
 * This bundle should be registered before any other bundles that require exclusive acess when starting.
 * In other words, using this bundle ensures that only one Dropwizard application on a server can start
 * while holding the lock.
 * <p>
 * The original use case for this bundle was to use it with {@link DynamicPortsBundle} to avoid
 * port-in-use conflicts when starting several Dropwizard applications on a server simultaneously.
 * In this use case, you first add this bundle, then the {@link DynamicPortsBundle} so that when
 * dynamic ports are assigned, it is guaranteed another Dropwizard application won't attempt to
 * use the same port. This matters mainly when the port range in which applications can use is
 * constrained to a relatively small range.
 * <p>
 * The original need to lock around dynamic port assignment is also the reason why
 * {@link StartupLockConfiguration} has a {@code useDynamicPorts} property, which is
 * then provided to {@link StartupLocker}. In a future release, we may begin to decouple
 * dynamic port assignment and this startup lock. For example, by providing a way that
 * users can specify whether the lock should be attempted or not via a boolean property
 * or function. The decoupling would also apply to {@link StartupLocker} so that the
 * boolean property or function is provided instead of {@link PortAssigner.PortAssignment} in
 * the {@link StartupLocker#acquireStartupLock acquireStartupLock} method.
 */
@Slf4j
public abstract class StartupLockBundle<C extends Configuration>
        implements ConfiguredBundle<C>, StartupLockConfigured<C> {

    @Override
    public void run(C configuration, Environment environment) throws Exception {
        LOG.trace("Running StartupLockBundle");

        var startupLocker = buildStartupLocker();

        var startupLockConfig = getStartupLockConfiguration(configuration);
        var portAssignment = portAssignmentFrom(startupLockConfig);

        var ip = requireNotNull(InetAddress.getLocalHost().getHostAddress(), "ip address must not be null");
        var lockPath = Paths.get(startupLockConfig.getZkStartupLockPath(), ip).toString();

        LOG.debug("Acquire startup lock with port assignment {} and lock path: {}", portAssignment, lockPath);

        var lockInfo = startupLocker.acquireStartupLock(
                lockPath,
                startupLockConfig.getZkStartupLockTimeout(),
                portAssignment,
                startupLockConfig.getCuratorConfig(),
                environment);

        LOG.debug("For lock {}, state is {} ({})", lockPath, lockInfo.getLockState(), lockInfo.getInfoMessage());

        LOG.trace("Add fallback startup listener");
        startupLocker.addFallbackJettyStartupLifeCycleListener(lockInfo, environment);

        LOG.trace("Add listener to release startup lock after server started");
        environment.lifecycle().addServerLifecycleListener(server -> {
            LOG.debug("Releasing startup lock if present");
            startupLocker.releaseStartupLockIfPresent(lockInfo);
        });
    }

    /**
     * The {@link SystemExecutioner} that will be passed to the {@link StartupLocker}
     * and which will be used if there is an error that causes the bundle to
     * terminate the application using {@link SystemExecutioner#exit()}.
     *
     * @return a new instance
     * @see StartupLocker
     */
    public SystemExecutioner getExecutioner() {
        return new SystemExecutioner();
    }

    @VisibleForTesting
    StartupLocker buildStartupLocker() {
        return StartupLocker.builder()
                .curatorFrameworkHelper(getCuratorFrameworkHelper())
                .curatorLockHelper(getCuratorLockHelper())
                .zkAvailabilityChecker(getKeeperAvailabilityChecker())
                .executioner(getExecutioner())
                .build();
    }

    @VisibleForTesting
    CuratorFrameworkHelper getCuratorFrameworkHelper() {
        return new CuratorFrameworkHelper();
    }

    @VisibleForTesting
    CuratorLockHelper getCuratorLockHelper() {
        return new CuratorLockHelper();
    }

    @VisibleForTesting
    ZooKeeperAvailabilityChecker getKeeperAvailabilityChecker() {
        return new ZooKeeperAvailabilityChecker();
    }
}
