package org.kiwiproject.dropwizard.util.startup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kiwiproject.dropwizard.util.startup.PortAssigner.PortAssignment.DYNAMIC;
import static org.kiwiproject.dropwizard.util.startup.PortAssigner.PortAssignment.STATIC;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.dropwizard.core.setup.Environment;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
        void shouldDefaultSystemExecutioner() {
            var builder = StartupLocker.builder().build();
            assertThat(builder.getExecutioner()).isNotNull();
        }

        @Test
        void shouldDefaultZooKeeperAvailabilityChecker() {
            var builder = StartupLocker.builder().build();
            assertThat(builder.getZkAvailabilityChecker()).isNotNull();
        }

        @Test
        void shouldDefaultCuratorFrameworkHelper() {
            var builder = StartupLocker.builder().build();
            assertThat(builder.getCuratorFrameworkHelper()).isNotNull();
        }
        @Test
        void shouldDefaultCuratorLockHelper() {
            var builder = StartupLocker.builder().build();
            assertThat(builder.getCuratorLockHelper()).isNotNull();
        }

    }
    @Nested
    class AcquireStartupLock {

        // NOTE: This is not static because there isn't a way to reset the lifecycle listeners for each test
        final DropwizardClientExtension clientExtension = new DropwizardClientExtension();

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

            assertThat(lockInfo.getLockState()).isEqualTo(StartupLockInfo.LockState.NOT_ATTEMPTED);
            assertThat(lockInfo.getInfoMessage()).isEqualTo("Using static port assignment. Lock not needed.");
            assertThat(lockInfo.getLock()).isNull();
            assertThat(lockInfo.getLockPath()).isBlank();
            assertThat(lockInfo.getException()).isNull();
            assertThat(lockInfo.getClient()).isNull();
        }

        @Test
        void shouldNotAttemptToAcquireLock_WhenUnableToConnectToZooKeeper() {
            var locker = startupLockerBuilder.build();

            var lockInfo = locker.acquireStartupLock(LOCK_PATH, Duration.milliseconds(100), DYNAMIC,
                    new CuratorConfig(), mock(Environment.class));

            assertThat(lockInfo.getLockState()).isEqualTo(StartupLockInfo.LockState.NOT_ATTEMPTED);
            assertThat(lockInfo.getInfoMessage()).startsWith("No ZooKeepers are available from connect string ");
            assertThat(lockInfo.getLock()).isNull();
            assertThat(lockInfo.getLockPath()).isBlank();
            assertThat(lockInfo.getException()).isNull();
            assertThat(lockInfo.getClient()).isNull();
        }

        @Test
        void shouldAttemptToAcquireLock_AndReturnFailStatus_WhenLockIsNotAcquired() {
            var curatorLockHelper = mock(CuratorLockHelper.class);
            var locker = startupLockerBuilder.curatorLockHelper(curatorLockHelper).build();

            var lock = mock(InterProcessMutex.class);
            when(curatorLockHelper.createInterProcessMutex(any(CuratorFramework.class), eq(LOCK_PATH))).thenReturn(lock);

            var exception = new LockAcquisitionException("Oops");
            doThrow(exception).when(curatorLockHelper).acquire(any(InterProcessMutex.class), eq(100L), eq(TimeUnit.MILLISECONDS));

            var curatorConfig = CuratorConfig.copyOfWithZkConnectString(new CuratorConfig(), ZK_TEST_SERVER.getConnectString());
            var lockInfo = locker.acquireStartupLock(LOCK_PATH, Duration.milliseconds(100), DYNAMIC, curatorConfig, mock(Environment.class));

            assertThat(lockInfo.getLockState()).isEqualTo(StartupLockInfo.LockState.ACQUIRE_FAIL);
            assertThat(lockInfo.getInfoMessage()).isEqualTo("Failed to obtain startup lock");
            assertThat(lockInfo.getLock()).isNull();
            assertThat(lockInfo.getLockPath()).isBlank();
            assertThat(lockInfo.getException()).isSameAs(exception);
            assertThat(lockInfo.getClient()).isNull();
        }

        @Test
        void shouldAttemptAndSucceedInAcquiringLock() {
            var locker = startupLockerBuilder.build();
            var curatorConfig = CuratorConfig.copyOfWithZkConnectString(new CuratorConfig(), ZK_TEST_SERVER.getConnectString());
            var lockInfo = locker.acquireStartupLock(LOCK_PATH, Duration.milliseconds(100), DYNAMIC,
                    curatorConfig, clientExtension.getEnvironment());

            assertThat(lockInfo.getLockState()).isEqualTo(StartupLockInfo.LockState.ACQUIRED);
            assertThat(lockInfo.getInfoMessage()).isEqualTo("Lock acquired");
            assertThat(lockInfo.getLock()).isNotNull();
            assertThat(lockInfo.getLockPath()).isEqualTo(LOCK_PATH);
            assertThat(lockInfo.getException()).isNull();
            assertThat(lockInfo.getClient()).isNotNull();

            var listeners = DropwizardAppTests.lifeCycleListenersOf(clientExtension.getEnvironment().lifecycle());
            assertThat(listeners).hasAtLeastOneElementOfType(StartupWithLockJettyLifeCycleListener.class);
        }
    }

    @Nested
    class AddFallbackJettyStartupLifeCycleListener {

        // NOTE: This is not static because there isn't a way to reset the lifecycle listeners for each test
        final DropwizardClientExtension clientExtension = new DropwizardClientExtension();

        @Test
        void shouldNotAddListener_WhenLockInfoIsAcquired() {
            var locker = StartupLocker.builder().executioner(mock(SystemExecutioner.class)).build();

            var curatorClient = mock(CuratorFramework.class);
            var lock = mock(InterProcessLock.class);
            var lockInfo = StartupLockInfo.builder()
                    .client(curatorClient)
                    .lock(lock)
                    .lockPath(LOCK_PATH)
                    .lockState(StartupLockInfo.LockState.ACQUIRED)
                    .infoMessage("Lock acquired")
                    .build();

            locker.addFallbackJettyStartupLifeCycleListener(lockInfo, clientExtension.getEnvironment());

            var listeners = DropwizardAppTests.lifeCycleListenersOf(clientExtension.getEnvironment().lifecycle());
            assertThat(listeners).doesNotHaveAnyElementsOfTypes(StartupJettyLifeCycleListener.class);
        }

        @ParameterizedTest
        @ValueSource(strings = { "NOT_ATTEMPTED", "ACQUIRE_FAIL" })
        void shouldAddListener_WhenLockInfoNotAcquired(String state) {
            var locker = StartupLocker.builder().executioner(mock(SystemExecutioner.class)).build();

            var info = StartupLockInfo.builder()
                    .lockState(StartupLockInfo.LockState.valueOf(state))
                    .infoMessage("Info about why lock not acquired")
                    .exception(new RuntimeException("Oops"))
                    .build();

            locker.addFallbackJettyStartupLifeCycleListener(info, clientExtension.getEnvironment());

            var listeners = DropwizardAppTests.lifeCycleListenersOf(clientExtension.getEnvironment().lifecycle());
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
        void shouldCleanupLock_WhenLockIsAcquired() {
            var curatorClient = mock(CuratorFramework.class);
            var lock = mock(InterProcessLock.class);
            var lockInfo = StartupLockInfo.builder()
                    .client(curatorClient)
                    .lock(lock)
                    .lockPath(LOCK_PATH)
                    .lockState(StartupLockInfo.LockState.ACQUIRED)
                    .infoMessage("Lock Acquired")
                    .build();

            locker.releaseStartupLockIfPresent(lockInfo);

            verify(curatorLockHelper).releaseLockQuietlyIfHeld(lock);
            verify(curatorFrameworkHelper).closeIfStarted(curatorClient);
        }

        @ParameterizedTest
        @ValueSource(strings = { "NOT_ATTEMPTED", "ACQUIRE_FAIL" })
        void shouldNotDoAnything_WhenLockIsNotAcquired(String state) {
            var info = StartupLockInfo.builder()
                    .lockState(StartupLockInfo.LockState.valueOf(state))
                    .infoMessage("Reason why lock was not acquired")
                    .exception(new RuntimeException("oops"))
                    .build();

            locker.releaseStartupLockIfPresent(info);

            verifyNoInteractions(curatorLockHelper, curatorFrameworkHelper);
        }
    }
}
