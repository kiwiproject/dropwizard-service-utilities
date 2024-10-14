package org.kiwiproject.dropwizard.util.startup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.kiwiproject.dropwizard.util.exception.NoAvailablePortException;
import org.kiwiproject.net.LocalPortChecker;

@DisplayName("RandomFreePortFinder")
class RandomFreePortFinderTest {

    @RepeatedTest(10)
    void shouldCreateRandomFreePortFinder() {
        var portFinder = new RandomFreePortFinder();
        var minPortNumber = 32_768;
        var maxPortNumber = 49_151;
        var range = new AllowablePortRange(minPortNumber, maxPortNumber);

        var servicePorts = portFinder.find(range);

        assertAll(
                () -> assertThat(servicePorts.applicationPort()).isBetween(minPortNumber, maxPortNumber),
                () -> assertThat(servicePorts.adminPort()).isBetween(minPortNumber, maxPortNumber),
                () -> assertThat(servicePorts.applicationPort()).isNotEqualTo(servicePorts.adminPort())
        );
    }

    @Test
    void shouldRequireLocalPortChecker() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RandomFreePortFinder(null))
                .withMessage("localPortChecker must not be null");
    }

    @Test
    void shouldReturnZeroPorts_WhenAllowablePortRangeIsNull() {
        var checker = mock(LocalPortChecker.class);
        var portFinder = new RandomFreePortFinder(checker);
        var servicePorts = portFinder.find(null);

        assertAll(
                () -> assertThat(servicePorts.applicationPort()).isZero(),
                () -> assertThat(servicePorts.adminPort()).isZero()
        );

        verifyNoInteractions(checker);
    }

    @RepeatedTest(25)
    void shouldFindRandomAvailablePortsInAllowableRange() {
        var minPortNumber = 9_000;
        var maxPortNumber = 9_010;
        var range = new AllowablePortRange(minPortNumber, maxPortNumber);
        var checker = mock(LocalPortChecker.class);
        when(checker.isPortAvailable(anyInt())).thenReturn(true);

        var portFinder = new RandomFreePortFinder(checker);
        var servicePorts = portFinder.find(range);

        assertAll(
                () -> assertThat(servicePorts.applicationPort()).isBetween(minPortNumber, maxPortNumber),
                () -> assertThat(servicePorts.adminPort()).isBetween(minPortNumber, maxPortNumber),
                () -> assertThat(servicePorts.applicationPort()).isNotEqualTo(servicePorts.adminPort())
        );

        verify(checker, times(2)).isPortAvailable(anyInt());
    }

    @RepeatedTest(10)
    void shouldFindAvailablePortsAfterTheFirstFewAreNotOpen() {
        var minPortNumber = 9_000;
        var maxPortNumber = 9_002;
        var range = new AllowablePortRange(minPortNumber, maxPortNumber);
        var checker = mock(LocalPortChecker.class);
        when(checker.isPortAvailable(anyInt()))
                .thenReturn(false)
                .thenReturn(false)
                .thenReturn(false)
                .thenReturn(true)
                .thenReturn(true);

        var portFinder = new RandomFreePortFinder(checker);
        var servicePorts = portFinder.find(range);

        assertAll(
                () -> assertThat(servicePorts.applicationPort()).isBetween(minPortNumber, maxPortNumber),
                () -> assertThat(servicePorts.adminPort()).isBetween(minPortNumber, maxPortNumber),
                () -> assertThat(servicePorts.applicationPort()).isNotEqualTo(servicePorts.adminPort())
        );

        verify(checker, times(5)).isPortAvailable(anyInt());
    }

    @Test
    void shouldThrowNoAvailablePortException_WhenNoPortsCanBeFound() {
        var minPortNumber = 9_000;
        var maxPortNumber = 9_001;
        var range = new AllowablePortRange(minPortNumber, maxPortNumber);
        var checker = mock(LocalPortChecker.class);
        when(checker.isPortAvailable(anyInt())).thenReturn(false);

        var portFinder = new RandomFreePortFinder(checker);

        assertThatThrownBy(() -> portFinder.find(range))
                .isInstanceOf(NoAvailablePortException.class)
                .hasMessage("Could not find an available port between %d and %d after 6 attempts. I give up.",
                        minPortNumber, maxPortNumber);

        verify(checker, times(6)).isPortAvailable(anyInt());
    }
}
