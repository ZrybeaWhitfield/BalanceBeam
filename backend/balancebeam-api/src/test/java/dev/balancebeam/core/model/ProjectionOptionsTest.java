package dev.balancebeam.core.model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ProjectionOptions")
class ProjectionOptionsTest {

    @Test
    @DisplayName("Defaults to 780 biweekly windows")
    void defaultOptions_uses780Windows() {
        assertEquals(780, new ProjectionOptions().maxWindows());
    }

    @Test
    @DisplayName("Accepts a single-window projection")
    void oneWindow_constructsSuccessfully() {
        assertDoesNotThrow(() -> new ProjectionOptions(1));
    }

    @Test
    @DisplayName("Rejects a zero-window limit")
    void zeroWindows_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new ProjectionOptions(0));
    }

    @Test
    @DisplayName("Rejects a negative window limit")
    void negativeWindows_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new ProjectionOptions(-1));
    }
}
