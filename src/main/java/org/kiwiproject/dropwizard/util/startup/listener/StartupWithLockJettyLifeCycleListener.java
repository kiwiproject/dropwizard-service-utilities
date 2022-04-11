package org.kiwiproject.dropwizard.util.startup.listener;

import static org.kiwiproject.base.KiwiPreconditions.requireNotBlank;
import static org.kiwiproject.base.KiwiPreconditions.requireNotNull;

import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.eclipse.jetty.util.component.LifeCycle;
import org.kiwiproject.curator.CuratorFrameworkHelper;
import org.kiwiproject.curator.CuratorLockHelper;
import org.kiwiproject.dropwizard.util.startup.SystemExecutioner;

/**
 * A Jetty {@link LifeCycle.Listener} that releases the ZooKeeper startup lock if
 * it exists. In the case of a lifecycle failure, it shuts down the system.
 */
@Slf4j
public class StartupWithLockJettyLifeCycleListener implements LifeCycle.Listener {

    private final CuratorFramework curatorFramework;
    private final InterProcessLock lock;
    private final String lockPath;
    private final CuratorLockHelper curatorLockHelper = new CuratorLockHelper();
    private final CuratorFrameworkHelper curatorFrameworkHelper = new CuratorFrameworkHelper();
    private final SystemExecutioner executioner;

    public StartupWithLockJettyLifeCycleListener(CuratorFramework curatorFramework,
                                                 InterProcessLock lock,
                                                 String lockPath,
                                                 SystemExecutioner executioner) {
        this.curatorFramework = requireNotNull(curatorFramework);
        this.lock = requireNotNull(lock);
        this.lockPath = requireNotBlank(lockPath);
        this.executioner = requireNotNull(executioner);
    }

    @Override
    public void lifeCycleFailure(LifeCycle event, Throwable cause) {
        LOG.error("Jetty LifeCycleFailure with event [{}]. Releasing lock [{}] on path [{}] and exiting the JVM!",
                event, lock, lockPath, cause);
        releaseLockAndClose();
        executioner.exit();
    }

    @Override
    public void lifeCycleStarted(LifeCycle event) {
        LOG.trace("Jetty LifeCycleStarted with event [{}]. Releasing lock [{}] on path [{}].", event, lock, lockPath);
        releaseLockAndClose();
    }

    @Override
    public void lifeCycleStopped(LifeCycle event) {
        LOG.trace("Jetty LifeCycleStopped with event [{}]. Releasing lock [{}] on path [{}] if still held (which is highly unlikely).",
                event, lock, lockPath);
        releaseLockAndClose();
    }

    private void releaseLockAndClose() {
        curatorLockHelper.releaseLockQuietlyIfHeld(lock);
        curatorFrameworkHelper.closeQuietly(curatorFramework);
    }
}
