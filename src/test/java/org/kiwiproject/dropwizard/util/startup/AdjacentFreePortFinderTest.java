package org.kiwiproject.dropwizard.util.startup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.kiwiproject.dropwizard.util.exception.NoAvailablePortException;
import org.kiwiproject.net.LocalPortChecker;

@DisplayName("AdjacentFreePortFinder")
class AdjacentFreePortFinderTest {

    @Test
    void shouldRequireLocalPortChecker() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new AdjacentFreePortFinder(null))
                .withMessage("localPortChecker must not be null");
    }

    @Test
    void shouldCreateIncrementingFreePortFinder() {
        var portFinder = new AdjacentFreePortFinder();
        var minPortNumber = 32_768;
        var maxPortNumber = 49_151;
        var range = new AllowablePortRange(minPortNumber, maxPortNumber);

        var servicePorts = portFinder.find(range);

        assertAll(
                () -> assertThat(servicePorts.applicationPort()).isBetween(minPortNumber, maxPortNumber),
                () -> assertThat(servicePorts.adminPort()).isBetween(minPortNumber, maxPortNumber),
                () -> assertThat(servicePorts.adminPort()).isEqualTo(servicePorts.applicationPort() + 1)
        );
    }

    @Test
    void shouldNotAllowNull_AllowablePortRange() {
        var portFinder = new AdjacentFreePortFinder();
        assertThatIllegalArgumentException()
                .isThrownBy(() -> portFinder.find(null))
                .withMessage("portRange must not be null");
    }

    @Test
    void shouldFindNextAdjacentApplicationAndAdminPorts_WhenFirstTwoAreNotInUse() {
        var checker = mock(LocalPortChecker.class);
        var portFinder = new AdjacentFreePortFinder(checker);

        when(checker.isPortAvailable(anyInt())).thenReturn(true);

        var minPortNumber = 35_000;
        var maxPortNumber = 36_000;

        var servicePorts = portFinder.find(new AllowablePortRange(minPortNumber, maxPortNumber));

        assertAll(
                () -> assertThat(servicePorts.applicationPort()).isEqualTo(minPortNumber),
                () -> assertThat(servicePorts.adminPort()).isEqualTo(minPortNumber + 1)
        );

        verify(checker).isPortAvailable(minPortNumber);
        verify(checker).isPortAvailable(minPortNumber + 1);
        verifyNoMoreInteractions(checker);
    }

    @Test
    void shouldFindNextAdjacentApplicationAndAdminPorts_WhenSomeAreInUse() {
        var checker = mock(LocalPortChecker.class);
        var portFinder = new AdjacentFreePortFinder(checker);

        when(checker.isPortAvailable(anyInt()))
                .thenReturn(false)
                .thenReturn(true)
                .thenReturn(false)
                .thenReturn(false)
                .thenReturn(false)
                .thenReturn(true)
                .thenReturn(false)
                .thenReturn(true)
                .thenReturn(false)
                .thenReturn(true)
                .thenReturn(false)
                .thenReturn(false)
                .thenReturn(false)
                .thenReturn(true)
                .thenReturn(false)
                .thenReturn(true)
                .thenReturn(true);

        var minPortNumber = 9_000;
        var maxPortNumber = 9_050;

        var servicePorts = portFinder.find(new AllowablePortRange(minPortNumber, maxPortNumber));

        assertAll(
                () -> assertThat(servicePorts.applicationPort()).isBetween(minPortNumber, maxPortNumber),
                () -> assertThat(servicePorts.adminPort()).isBetween(minPortNumber, maxPortNumber),
                () -> assertThat(servicePorts.adminPort()).isEqualTo(servicePorts.applicationPort() + 1)
        );

        verify(checker, times(17)).isPortAvailable(anyInt());
    }

    @Test
    void shouldThrowNoAvailablePortException_WhenNoPortsCanBeFound() {
        var checker = mock(LocalPortChecker.class);
        var portFinder = new AdjacentFreePortFinder(checker);

        when(checker.isPortAvailable(anyInt())).thenReturn(false);

        var minPortNumber = 9_000;
        var maxPortNumber = 9_025;
        var portRange = new AllowablePortRange(minPortNumber, maxPortNumber);

        assertThatExceptionOfType(NoAvailablePortException.class)
                .isThrownBy(() -> portFinder.find(portRange))
                .withMessage("Could not find two adjacent open ports between %d and %d",
                        minPortNumber, maxPortNumber);

        verify(checker, times(portRange.getNumPortsInRange() - 1)).isPortAvailable(anyInt());
    }
}
