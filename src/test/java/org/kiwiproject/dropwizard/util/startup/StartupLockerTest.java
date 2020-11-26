package org.kiwiproject.dropwizard.util.startup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.kiwiproject.dropwizard.util.startup.PortAssigner.PortAssignment.DYNAMIC;
import static org.kiwiproject.dropwizard.util.startup.PortAssigner.PortAssignment.STATIC;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.dropwizard.setup.Environment;
import io.dropwizard.testing.junit5.DropwizardClientExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.util.Duration;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.kiwiproject.curator.CuratorFrameworkHelper;
import org.kiwiproject.curator.CuratorLockHelper;
import org.kiwiproject.curator.config.CuratorConfig;
import org.kiwiproject.curator.exception.LockAcquisitionException;
import org.kiwiproject.dropwizard.util.startup.listener.StartupJettyLifeCycleListener;
import org.kiwiproject.dropwizard.util.startup.listener.StartupWithLockJettyLifeCycleListener;
import org.kiwiproject.test.curator.CuratorTestingServerExtension;
import org.kiwiproject.test.dropwizard.app.DropwizardAppTests;

import java.util.concurrent.TimeUnit;

@DisplayName("StartupLocker")
@ExtendWith(DropwizardExtensionsSupport.class)
class StartupLockerTest {

    private static final String LOCK_PATH = "/core-service/tests/lock-00000001";

    @RegisterExtension
    static final CuratorTestingServerExtension ZK_TEST_SERVER = new CuratorTestingServerExtension();

    @Nested
    class Builder {

        @Test
        void shouldRequireSystemExecutioner() {
            var builder = StartupLocker.builder();

            assertThatThrownBy(builder::build)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("SystemExecutioner is required");
        }

        @Test
        void shouldDefaultZooKeeperAvailabilityChecker() {
            var builder = StartupLocker.builder()
                    .executioner(new SystemExecutioner())
                    .build();

            assertThat(builder.getZkAvailabilityChecker()).isNotNull();
        }

        @Test
        void shouldDefaultCuratorFrameworkHelper() {
            var builder = StartupLocker.builder()
                    .executioner(new SystemExecutioner())
                    .build();

            assertThat(builder.getCuratorFrameworkHelper()).isNotNull();
        }
        @Test
        void shouldDefaultCuratorLockHelper() {
            var builder = StartupLocker.builder()
                    .executioner(new SystemExecutioner())
                    .build();

            assertThat(builder.getCuratorLockHelper()).isNotNull();
        }

    }
    @Nested
    class AcquireStartupLock {

        final DropwizardClientExtension CLIENT_EXTENSION = new DropwizardClientExtension();

        private StartupLocker.StartupLockerBuilder startupLockerBuilder;

        @BeforeEach
        void setUp() {
            // We don't want to accidentally kill the JVM!
            var systemExecutioner = mock(SystemExecutioner.class);

            startupLockerBuilder = StartupLocker.builder()
                    .executioner(systemExecutioner);
        }

        @Test
        void shouldNotAttemptToAcquireLock_WhenUsingStaticPorts() {
            var locker = startupLockerBuilder.build();

            var lockInfo = locker.acquireStartupLock(LOCK_PATH, Duration.milliseconds(100),
                    STATIC, new CuratorConfig(), mock(Environment.class));

            assertThat(lockInfo).isNull();
        }

        @Test
        void shouldNotAttemptToAcquireLock_WhenUnableToConnectToZooKeeper() {
            var locker = startupLockerBuilder.build();

            var lockInfo = locker.acquireStartupLock(LOCK_PATH, Duration.milliseconds(100), DYNAMIC,
                    new CuratorConfig(), mock(Environment.class));

            assertThat(lockInfo).isNull();
        }

        @Test
        void shouldAttemptToAcquireLock_AndReturnNull_WhenLockIsNotAcquired() {
            var curatorLockHelper = mock(CuratorLockHelper.class);
            var locker = startupLockerBuilder.curatorLockHelper(curatorLockHelper).build();

            var lock = mock(InterProcessMutex.class);
            when(curatorLockHelper.createInterProcessMutex(any(CuratorFramework.class), eq(LOCK_PATH))).thenReturn(lock);
            doThrow(new LockAcquisitionException("Oops")).when(curatorLockHelper).acquire(any(InterProcessMutex.class), eq(100L), eq(TimeUnit.MILLISECONDS));

            var curatorConfig = CuratorConfig.copyOfWithZkConnectString(new CuratorConfig(), ZK_TEST_SERVER.getConnectString());
            var lockInfo = locker.acquireStartupLock(LOCK_PATH, Duration.milliseconds(100), DYNAMIC, curatorConfig, mock(Environment.class));

            assertThat(lockInfo).isNull();
        }

        @Test
        void shouldAttemptAndSucceedInAcquiringLock() {
            var locker = startupLockerBuilder.build();
            var curatorConfig = CuratorConfig.copyOfWithZkConnectString(new CuratorConfig(), ZK_TEST_SERVER.getConnectString());
            var lockInfo = locker.acquireStartupLock(LOCK_PATH, Duration.milliseconds(100), DYNAMIC,
                    curatorConfig, CLIENT_EXTENSION.getEnvironment());

            assertThat(lockInfo).isNotNull();
            assertThat(lockInfo.lockPath).isEqualTo(LOCK_PATH);

            var listeners = DropwizardAppTests.lifeCycleListenersOf(CLIENT_EXTENSION.getEnvironment().lifecycle());
            assertThat(listeners).hasAtLeastOneElementOfType(StartupWithLockJettyLifeCycleListener.class);
        }
    }

    @Nested
    class AddFallbackJettyStartupLifeCycleListener {

        final DropwizardClientExtension CLIENT_EXTENSION = new DropwizardClientExtension();

        @Test
        void shouldNotAddListener_WhenLockInfoIsNotNull() {
            var locker = StartupLocker.builder().executioner(mock(SystemExecutioner.class)).build();

            var curatorClient = mock(CuratorFramework.class);
            var lock = mock(InterProcessLock.class);
            var lockInfo = new StartupLocker.StartupLockInfo(curatorClient, lock, LOCK_PATH);
            locker.addFallbackJettyStartupLifeCycleListener(lockInfo, CLIENT_EXTENSION.getEnvironment());

            var listeners = DropwizardAppTests.lifeCycleListenersOf(CLIENT_EXTENSION.getEnvironment().lifecycle());
            assertThat(listeners).doesNotHaveAnyElementsOfTypes(StartupJettyLifeCycleListener.class);
        }

        @Test
        void shouldAddListener_WhenLockInfoIsNull() {
            var locker = StartupLocker.builder().executioner(mock(SystemExecutioner.class)).build();

            locker.addFallbackJettyStartupLifeCycleListener(null, CLIENT_EXTENSION.getEnvironment());

            var listeners = DropwizardAppTests.lifeCycleListenersOf(CLIENT_EXTENSION.getEnvironment().lifecycle());
            assertThat(listeners).hasAtLeastOneElementOfType(StartupJettyLifeCycleListener.class);
        }
    }

    @Nested
    class ReleaseStartupLockIfPresent {

        private CuratorLockHelper curatorLockHelper;
        private CuratorFrameworkHelper curatorFrameworkHelper;
        private StartupLocker locker;

        @BeforeEach
        void setUp() {
            curatorLockHelper = mock(CuratorLockHelper.class);
            curatorFrameworkHelper = mock(CuratorFrameworkHelper.class);
            locker = StartupLocker.builder()
                    .executioner(mock(SystemExecutioner.class))
                    .curatorLockHelper(curatorLockHelper)
                    .curatorFrameworkHelper(curatorFrameworkHelper)
                    .build();
        }

        @Test
        void shouldCleanupLock_WhenInfoIsNotNull() {
            var curatorClient = mock(CuratorFramework.class);
            var lock = mock(InterProcessLock.class);
            var lockInfo = new StartupLocker.StartupLockInfo(curatorClient, lock, LOCK_PATH);

            locker.releaseStartupLockIfPresent(lockInfo);

            verify(curatorLockHelper).releaseLockQuietlyIfHeld(lock);
            verify(curatorFrameworkHelper).closeIfStarted(curatorClient);
        }

        @Test
        void shouldNotDoAnything_WhenInfoIsNull() {
            locker.releaseStartupLockIfPresent(null);

            verifyNoInteractions(curatorLockHelper, curatorFrameworkHelper);
        }
    }
}
