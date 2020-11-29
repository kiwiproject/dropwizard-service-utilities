package org.kiwiproject.dropwizard.util.startup.listener;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.imps.CuratorFrameworkState;
import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.kiwiproject.dropwizard.util.startup.SystemExecutioner;

@DisplayName("StartupWithLockJettyLifeCycleListener")
class StartupWithLockJettyLifeCycleListenerTest {

    private StartupWithLockJettyLifeCycleListener listener;
    private CuratorFramework client;
    private InterProcessLock lock;
    private SystemExecutioner executioner;

    @BeforeEach
    void setUp() {
        client = mock(CuratorFramework.class);
        lock = mock(InterProcessLock.class);
        executioner = mock(SystemExecutioner.class);
        listener = new StartupWithLockJettyLifeCycleListener(client, lock, "/core-service/tests/lock-00000001", executioner);
    }

    @Test
    void testLifeCycleStarted_WhenWeOwnTheLock() throws Exception {
        when(lock.isAcquiredInThisProcess()).thenReturn(true);
        when(client.getState()).thenReturn(CuratorFrameworkState.STARTED);

        listener.lifeCycleStarted(null);

        verify(lock).release();
        verify(client).close();
    }

    @Test
    void testLifeCycleFailure_WhenWeOwnTheLock() throws Exception {
        when(lock.isAcquiredInThisProcess()).thenReturn(true);
        when(client.getState()).thenReturn(CuratorFrameworkState.STARTED);

        listener.lifeCycleFailure(null, new Exception(("bad shit happened")));

        verify(lock).release();
        verify(client).close();
        verify(executioner).exit();
    }

    @Test
    void testLifeCycleStopped_WhenWeOwnTheLock() throws Exception {
        when(lock.isAcquiredInThisProcess()).thenReturn(true);
        when(client.getState()).thenReturn(CuratorFrameworkState.STARTED);

        listener.lifeCycleStopped(null);

        verify(lock).release();
        verify(client).close();
    }
}
