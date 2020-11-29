package org.kiwiproject.dropwizard.util.startup;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("StartupLockInfo")
class StartupLockInfoTest {

    @Test
    void shouldRequireInfoMessage() {
        var builder = StartupLockInfo.builder();

        assertThatThrownBy(builder::build)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("infoMessage is required");
    }

    @Test
    void shouldRequireLockState() {
        var builder = StartupLockInfo.builder()
                .infoMessage("lock acquired");

        assertThatThrownBy(builder::build)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("lockState is required");
    }

    @Test
    void shouldRequireClientIsRequired_WhenLockStateIsAcquired() {
        var builder = StartupLockInfo.builder()
                .infoMessage("lock acquired")
                .lockState(StartupLockInfo.LockState.ACQUIRED);

        assertThatThrownBy(builder::build)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("client is required");
    }

    @Test
    void shouldRequireLockIsRequired_WhenLockStateIsAcquired() {
        var builder = StartupLockInfo.builder()
                .infoMessage("lock acquired")
                .lockState(StartupLockInfo.LockState.ACQUIRED)
                .client(mock(CuratorFramework.class));

        assertThatThrownBy(builder::build)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("lock is required");
    }

    @Test
    void shouldRequireLockPathIsRequired_WhenLockStateIsAcquired() {
        var builder = StartupLockInfo.builder()
                .infoMessage("lock acquired")
                .lockState(StartupLockInfo.LockState.ACQUIRED)
                .client(mock(CuratorFramework.class))
                .lock(mock(InterProcessLock.class));

        assertThatThrownBy(builder::build)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("lockPath is required");
    }

    @Test
    void shouldRequireExceptionIsRequired_WhenLockStateIsAcquireFail() {
        var builder = StartupLockInfo.builder()
                .infoMessage("lock acquired")
                .lockState(StartupLockInfo.LockState.ACQUIRE_FAIL);

        assertThatThrownBy(builder::build)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("exception is required");
    }

    @Test
    void shouldOnlyRequireInfoMessageAndLockState_WhenLockStateIsNotAttempted() {
        var builder = StartupLockInfo.builder()
                .infoMessage("lock acquired")
                .lockState(StartupLockInfo.LockState.NOT_ATTEMPTED);

        assertThatCode(builder::build).doesNotThrowAnyException();
    }
}
