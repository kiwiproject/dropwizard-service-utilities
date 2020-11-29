package org.kiwiproject.dropwizard.util.startup;

import static org.kiwiproject.base.KiwiPreconditions.requireNotBlank;
import static org.kiwiproject.base.KiwiPreconditions.requireNotNull;

import lombok.Builder;
import lombok.Getter;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.locks.InterProcessLock;

/**
 * A value class that contains information about a startup lock, such as whether a lock was successfully
 * acquired, the lock path, the lock itself, as well as information when any exception occurs.
 * <p>
 * NOTE: There is an assumption here that any users of this class will check the {@code lockState} prior to accessing other fields.
 * Not all fields are populated for every state. For instance, {@code client}, {@code lock}, and {@code lockPath} are only used when
 * lock state is {@code ACQUIRED}, where as {@code exception} is only used when lock state is {@code ACQUIRE_FAIL}.
 */
@Getter
public class StartupLockInfo {

    public enum LockState {
        NOT_ATTEMPTED, ACQUIRED, ACQUIRE_FAIL
    }

    private CuratorFramework client;
    private InterProcessLock lock;
    private String lockPath;
    private final LockState lockState;
    private final String infoMessage;
    private Exception exception;

    @Builder
    StartupLockInfo(CuratorFramework client,
                    InterProcessLock lock,
                    String lockPath,
                    LockState lockState,
                    String infoMessage,
                    Exception exception) {

        this.infoMessage = requireNotBlank(infoMessage, "infoMessage is required");
        this.lockState = requireNotNull(lockState, "lockState is required");

        if (lockState == LockState.ACQUIRED) {
            this.client = requireNotNull(client, "client is required");
            this.lock = requireNotNull(lock, "lock is required");
            this.lockPath = requireNotBlank(lockPath, "lockPath is required");
        } else if (lockState == LockState.ACQUIRE_FAIL) {
            this.exception = requireNotNull(exception, "exception is required");
        }
    }
}
