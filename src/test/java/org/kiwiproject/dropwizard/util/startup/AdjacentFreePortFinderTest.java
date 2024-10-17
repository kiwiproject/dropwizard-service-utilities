package org.kiwiproject.dropwizard.util.startup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
    void shouldFindLastTwoPortsInRange_WhenAllOthersAreInUse() {
        var checker = mock(LocalPortChecker.class);
        var portFinder = new AdjacentFreePortFinder(checker);

        when(checker.isPortAvailable(anyInt()))
                .thenReturn(false)  // 9000
                .thenReturn(false)  // 9001
                .thenReturn(false)  // 9002
                .thenReturn(false)  // 9003
                .thenReturn(true)   // 9004
                .thenReturn(true);  // 9005

        var minPortNumber = 9_000;
        var maxPortNumber = 9_005;

        var servicePorts = portFinder.find(new AllowablePortRange(minPortNumber, maxPortNumber));

        assertAll(
                () -> assertThat(servicePorts.applicationPort()).isEqualTo(9_004),
                () -> assertThat(servicePorts.adminPort()).isEqualTo(9_005)
        );

        verify(checker).isPortAvailable(9_000);
        verify(checker).isPortAvailable(9_001);
        verify(checker).isPortAvailable(9_002);
        verify(checker).isPortAvailable(9_003);
        verify(checker).isPortAvailable(9_004);
        verify(checker).isPortAvailable(9_005);
        verifyNoMoreInteractions(checker);
    }

    @Test
    void shouldThrowNoAvailablePortException_WhenAllButLastPortAreInUse() {
        var checker = mock(LocalPortChecker.class);
        var portFinder = new AdjacentFreePortFinder(checker);

        when(checker.isPortAvailable(anyInt()))
                .thenReturn(false)  // 9000
                .thenReturn(false)  // 9001
                .thenReturn(false)  // 9002
                .thenReturn(false)  // 9003
                .thenReturn(false)  // 9004
                .thenReturn(true);  // 9005

        var minPortNumber = 9_000;
        var maxPortNumber = 9_005;

        var portRange = new AllowablePortRange(minPortNumber, maxPortNumber);

        assertThatExceptionOfType(NoAvailablePortException.class)
                .isThrownBy(() -> portFinder.find(portRange))
                .withMessage("Could not find two adjacent open ports between %d and %d",
                        minPortNumber, maxPortNumber);

        verify(checker).isPortAvailable(9_000);
        verify(checker).isPortAvailable(9_001);
        verify(checker).isPortAvailable(9_002);
        verify(checker).isPortAvailable(9_003);
        verify(checker).isPortAvailable(9_004);
        verify(checker, never()).isPortAvailable(9_005);
        verifyNoMoreInteractions(checker);
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

        int expectedNumberOfInvocations = portRange.getNumPortsInRange() - 1;
        verify(checker, times(expectedNumberOfInvocations)).isPortAvailable(anyInt());
    }
}
