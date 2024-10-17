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

@DisplayName("IncrementingFreePortFinder")
class IncrementingFreePortFinderTest {

    @Test
    void shouldRequireLocalPortChecker() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new IncrementingFreePortFinder(null))
                .withMessage("localPortChecker must not be null");
    }

    @Test
    void shouldCreateIncrementingFreePortFinder() {
        var portFinder = new IncrementingFreePortFinder();
        var minPortNumber = 32_768;
        var maxPortNumber = 49_151;
        var range = new AllowablePortRange(minPortNumber, maxPortNumber);

        var servicePorts = portFinder.find(range);

        assertAll(
                () -> assertThat(servicePorts.applicationPort()).isBetween(minPortNumber, maxPortNumber),
                () -> assertThat(servicePorts.adminPort()).isBetween(minPortNumber, maxPortNumber),
                () -> assertThat(servicePorts.adminPort()).isGreaterThan(servicePorts.applicationPort())
        );
    }

    @Test
    void shouldNotAllowNull_AllowablePortRange() {
        var portFinder = new IncrementingFreePortFinder();
        assertThatIllegalArgumentException()
                .isThrownBy(() -> portFinder.find(null))
                .withMessage("portRange must not be null");
    }

    @Test
    void shouldFindNextApplicationAndAdminPorts_WhenFirstTwoAreOpen() {
        var checker = mock(LocalPortChecker.class);
        var portFinder = new IncrementingFreePortFinder(checker);

        when(checker.isPortAvailable(anyInt())).thenReturn(true);

        var minPortNumber = 9_000;
        var maxPortNumber = 9_050;

        var servicePorts = portFinder.find(new AllowablePortRange(minPortNumber, maxPortNumber));

        assertAll(
                () -> assertThat(servicePorts.applicationPort()).isEqualTo(9_000),
                () -> assertThat(servicePorts.adminPort()).isEqualTo(9_001)
        );
    }

    @Test
    void shouldFindNextApplicationAndAdminPorts_WhenSomeAreInUse() {
        var checker = mock(LocalPortChecker.class);
        var portFinder = new IncrementingFreePortFinder(checker);

        when(checker.isPortAvailable(anyInt()))
                .thenReturn(false)  // 9000
                .thenReturn(false)  // 9001
                .thenReturn(true)   // 9002
                .thenReturn(false)  // 9003
                .thenReturn(false)  // 9004
                .thenReturn(true);  // 9005

        var minPortNumber = 9_000;
        var maxPortNumber = 9_050;

        var servicePorts = portFinder.find(new AllowablePortRange(minPortNumber, maxPortNumber));

        assertAll(
                () -> assertThat(servicePorts.applicationPort()).isEqualTo(9_002),
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
    void shouldFindLastTwoPortsInRange_WhenAllOthersAreInUse() {
        var checker = mock(LocalPortChecker.class);
        var portFinder = new IncrementingFreePortFinder(checker);

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
        var portFinder = new IncrementingFreePortFinder(checker);

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
                .withMessage("Could not find two open ports between %d and %d",
                        minPortNumber, maxPortNumber);

        verify(checker).isPortAvailable(9_000);
        verify(checker).isPortAvailable(9_001);
        verify(checker).isPortAvailable(9_002);
        verify(checker).isPortAvailable(9_003);
        verify(checker).isPortAvailable(9_004);
        verify(checker).isPortAvailable(9_005);
        verifyNoMoreInteractions(checker);
    }

    @Test
    void shouldThrowNoAvailablePortException_WhenNoPortsCanBeFound() {
        var checker = mock(LocalPortChecker.class);
        var portFinder = new IncrementingFreePortFinder(checker);

        when(checker.isPortAvailable(anyInt())).thenReturn(false);

        var minPortNumber = 9_000;
        var maxPortNumber = 9_030;
        var portRange = new AllowablePortRange(minPortNumber, maxPortNumber);

        assertThatExceptionOfType(NoAvailablePortException.class)
                .isThrownBy(() -> portFinder.find(portRange))
                .withMessage("Could not find two open ports between %d and %d",
                        minPortNumber, maxPortNumber);

        var expectedNumberOfInvocations = portRange.getNumPortsInRange();
        verify(checker, times(expectedNumberOfInvocations)).isPortAvailable(anyInt());
        verifyNoMoreInteractions(checker);
    }
}
