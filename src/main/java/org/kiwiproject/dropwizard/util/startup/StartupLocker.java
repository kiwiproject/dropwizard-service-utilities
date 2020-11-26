package org.kiwiproject.dropwizard.util.startup;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.kiwiproject.base.KiwiPreconditions.requireNotNull;

import com.google.common.annotations.VisibleForTesting;
import io.dropwizard.setup.Environment;
import io.dropwizard.util.Duration;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.kiwiproject.curator.CuratorFrameworkHelper;
import org.kiwiproject.curator.CuratorLockHelper;
import org.kiwiproject.curator.config.CuratorConfig;
import org.kiwiproject.curator.exception.LockAcquisitionException;
import org.kiwiproject.curator.zookeeper.ZooKeeperAvailabilityChecker;
import org.kiwiproject.dropwizard.util.startup.PortAssigner.PortAssignment;
import org.kiwiproject.dropwizard.util.startup.listener.StartupJettyLifeCycleListener;
import org.kiwiproject.dropwizard.util.startup.listener.StartupWithLockJettyLifeCycleListener;

import java.util.Optional;

/**
 * Utilities to acquire and release a lock from ZooKeeper during startup of the service
 */
@Slf4j
public class StartupLocker {

    @Getter(AccessLevel.PACKAGE)
    @VisibleForTesting
    private final ZooKeeperAvailabilityChecker zkAvailabilityChecker;

    @Getter(AccessLevel.PACKAGE)
    @VisibleForTesting
    private final CuratorFrameworkHelper curatorFrameworkHelper;

    @Getter(AccessLevel.PACKAGE)
    @VisibleForTesting
    private final CuratorLockHelper curatorLockHelper;

    private final SystemExecutioner executioner;

    @Builder
    private StartupLocker(SystemExecutioner executioner,
                          ZooKeeperAvailabilityChecker zkAvailabilityChecker,
                          CuratorFrameworkHelper curatorFrameworkHelper,
                          CuratorLockHelper curatorLockHelper) {
        this.executioner = requireNotNull(executioner, "SystemExecutioner is required");
        this.zkAvailabilityChecker = Optional.ofNullable(zkAvailabilityChecker).orElse(new ZooKeeperAvailabilityChecker());
        this.curatorFrameworkHelper = Optional.ofNullable(curatorFrameworkHelper).orElse(new CuratorFrameworkHelper());
        this.curatorLockHelper = Optional.ofNullable(curatorLockHelper).orElse(new CuratorLockHelper());
    }

    @AllArgsConstructor
    public static class StartupLockInfo {
        final CuratorFramework client;
        final InterProcessLock lock;
        final String lockPath;
    }

    /**
     * Attempts to acquire a lock from ZooKeeper during startup.
     *
     * @param lockPath      the path in ZooKeeper to store the lock
     * @param lockTimeout   the amount of time to wait for the lock to be acquired
     * @param curatorConfig the Curator configuration
     * @param environment   the Dropwizard environment
     * @return information about the acquired lock or null if lock could not be acquired
     */
    public StartupLockInfo acquireStartupLock(String lockPath, Duration lockTimeout, PortAssignment assignment, CuratorConfig curatorConfig, Environment environment) {
        if (assignment == PortAssignment.STATIC) {
            return null;
        }

        if (zkAvailabilityChecker.anyZooKeepersAvailable(curatorConfig)) {
            var curatorFramework = curatorFrameworkHelper.startCuratorFramework(curatorConfig);
            var lock = curatorLockHelper.createInterProcessMutex(curatorFramework, lockPath);

            if (tryAcquireStartupLock(lock, lockPath, lockTimeout)) {
                environment.lifecycle().addLifeCycleListener(new StartupWithLockJettyLifeCycleListener(curatorFramework, lock, lockPath, executioner));
                return new StartupLockInfo(curatorFramework, lock, lockPath);
            } else {
                LOG.warn("Lock on path [{}] not obtained. Closing Curator.", lockPath);
                curatorFrameworkHelper.closeQuietly(curatorFramework);
            }
        } else {
            LOG.warn("No ZooKeepers are available from connect string [{}]", curatorConfig.getZkConnectString());
        }

        LOG.warn("Startup using dynamic ports will continue without ZooKeeper lock (which may result in port conflicts)");
        return null;
    }

    private boolean tryAcquireStartupLock(InterProcessMutex lock, String lockPath, Duration lockTimeout) {
        try {
            LOG.debug("Start lock acquisition for path [{}]. Timeout set to [{}]", lockPath, lockTimeout);
            curatorLockHelper.acquire(lock, lockTimeout.getQuantity(), lockTimeout.getUnit());
            LOG.debug("Acquired lock on path [{}]", lockPath);
            return true;
        } catch (LockAcquisitionException e) {
            LOG.warn("Failed to obtain startup lock. Allow startup to continue and maybe we will be lucky with port assignment!", e);
            return false;
        }
    }

    /**
     * Adds a {@link StartupJettyLifeCycleListener} in case the lock was never acquired.
     *
     * @param lockInfo      the lock info indicating if the lock was acquired
     * @param environment   the Dropwizard environment used to add the listener
     */
    public void addFallbackJettyStartupLifeCycleListener(StartupLockInfo lockInfo, Environment environment) {
        if (isNull(lockInfo)) {
            environment.lifecycle().addLifeCycleListener(new StartupJettyLifeCycleListener(executioner));
        }
    }

    /**
     * Cleans up the startup lock if it was acquired.
     *
     * @param lockInfo the lock info indicating if the lock was acquired
     */
    public void releaseStartupLockIfPresent(StartupLockInfo lockInfo) {
        if (nonNull(lockInfo)) {
            LOG.warn("Due to exception caught running app [{}], early releasing lock [{}] on path [{}]", this, lockInfo.lock, lockInfo.lockPath);
            curatorLockHelper.releaseLockQuietlyIfHeld(lockInfo.lock);
            curatorFrameworkHelper.closeIfStarted(lockInfo.client);
        }
    }
}
