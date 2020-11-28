package org.kiwiproject.dropwizard.util.startup;

import static org.kiwiproject.base.KiwiStrings.format;

import io.dropwizard.setup.Environment;
import io.dropwizard.util.Duration;
import lombok.AccessLevel;
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
@Getter(AccessLevel.PACKAGE) // For testing
public class StartupLocker {

    private final ZooKeeperAvailabilityChecker zkAvailabilityChecker;
    private final CuratorFrameworkHelper curatorFrameworkHelper;
    private final CuratorLockHelper curatorLockHelper;
    private final SystemExecutioner executioner;

    @Builder
    private StartupLocker(SystemExecutioner executioner,
                          ZooKeeperAvailabilityChecker zkAvailabilityChecker,
                          CuratorFrameworkHelper curatorFrameworkHelper,
                          CuratorLockHelper curatorLockHelper) {
        this.executioner = Optional.ofNullable(executioner).orElseGet(SystemExecutioner::new);
        this.zkAvailabilityChecker = Optional.ofNullable(zkAvailabilityChecker).orElseGet(ZooKeeperAvailabilityChecker::new);
        this.curatorFrameworkHelper = Optional.ofNullable(curatorFrameworkHelper).orElseGet(CuratorFrameworkHelper::new);
        this.curatorLockHelper = Optional.ofNullable(curatorLockHelper).orElseGet(CuratorLockHelper::new);
    }

    /**
     * Model object containing information on the state of the acquired lock and necessary objects needed to release the lock.
     */
    @Getter
    @Builder
    public static class StartupLockInfo {

        public enum LockState {
            NOT_ATTEMPTED, ACQUIRED, ACQUIRE_FAIL
        }

        CuratorFramework client;
        InterProcessLock lock;
        String lockPath;
        LockState lockState;
        String infoMessage;
        Exception exception;
    }

    /**
     * Attempts to acquire a lock from ZooKeeper during startup.
     *
     * @param lockPath      the path in ZooKeeper to store the lock
     * @param lockTimeout   the amount of time to wait for the lock to be acquired
     * @param curatorConfig the Curator configuration
     * @param environment   the Dropwizard environment
     * @return information about the acquired lock or {@link Optional#empty()} if lock could not be acquired
     */
    public StartupLockInfo acquireStartupLock(String lockPath,
                                              Duration lockTimeout,
                                              PortAssignment assignment,
                                              CuratorConfig curatorConfig,
                                              Environment environment) {

        if (assignment == PortAssignment.STATIC) {
            return StartupLockInfo.builder()
                    .lockState(StartupLockInfo.LockState.NOT_ATTEMPTED)
                    .infoMessage("Using static port assignment. Lock not needed.")
                    .build();
        }

        if (zkAvailabilityChecker.anyZooKeepersAvailable(curatorConfig)) {
            var curatorFramework = curatorFrameworkHelper.startCuratorFramework(curatorConfig);
            var lock = curatorLockHelper.createInterProcessMutex(curatorFramework, lockPath);

            try {
                tryAcquireStartupLock(lock, lockPath, lockTimeout);
                environment.lifecycle().addLifeCycleListener(new StartupWithLockJettyLifeCycleListener(curatorFramework, lock, lockPath, executioner));
                return StartupLockInfo.builder()
                        .client(curatorFramework)
                        .lock(lock)
                        .lockPath(lockPath)
                        .lockState(StartupLockInfo.LockState.ACQUIRED)
                        .infoMessage("Lock acquired")
                        .build();
            } catch (LockAcquisitionException e) {
                LOG.warn("Lock on path [{}] not obtained. Closing Curator.", lockPath);
                curatorFrameworkHelper.closeQuietly(curatorFramework);

                return StartupLockInfo.builder()
                        .lockState(StartupLockInfo.LockState.ACQUIRE_FAIL)
                        .infoMessage("Failed to obtain startup lock")
                        .exception(e)
                        .build();
            }
        }

        return StartupLockInfo.builder()
                .lockState(StartupLockInfo.LockState.NOT_ATTEMPTED)
                .infoMessage(format("No ZooKeepers are available from connect string [{}]", curatorConfig.getZkConnectString()))
                .build();
    }

    private void tryAcquireStartupLock(InterProcessMutex lock, String lockPath, Duration lockTimeout) {
        LOG.debug("Start lock acquisition for path [{}]. Timeout set to [{}]", lockPath, lockTimeout);
        curatorLockHelper.acquire(lock, lockTimeout.getQuantity(), lockTimeout.getUnit());
        LOG.debug("Acquired lock on path [{}]", lockPath);
    }

    /**
     * Adds a {@link StartupJettyLifeCycleListener} in case the lock was never acquired.
     *
     * @param lockInfo    the lock info indicating if the lock was acquired
     * @param environment the Dropwizard environment used to add the listener
     */
    public void addFallbackJettyStartupLifeCycleListener(StartupLockInfo lockInfo, Environment environment) {
        if (lockInfo.getLockState() != StartupLockInfo.LockState.ACQUIRED) {
            environment.lifecycle().addLifeCycleListener(new StartupJettyLifeCycleListener(executioner));
        }
    }

    /**
     * Cleans up the startup lock if it was acquired.
     *
     * @param lockInfo the lock info indicating if the lock was acquired
     */
    public void releaseStartupLockIfPresent(StartupLockInfo lockInfo) {
        if (lockInfo.getLockState() == StartupLockInfo.LockState.ACQUIRED) {
            LOG.warn("Due to exception caught running app [{}], early releasing lock [{}] on path [{}]", this, lockInfo.lock, lockInfo.lockPath);
            curatorLockHelper.releaseLockQuietlyIfHeld(lockInfo.lock);
            curatorFrameworkHelper.closeIfStarted(lockInfo.client);
        }
    }
}
