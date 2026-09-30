package com.agent.dock.process;

import org.junit.jupiter.api.Test;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutablesTest {

    @Test
    void resolvesKnownExecutableFromPath() {
        String resolved = Executables.resolve(Executables.isWindows() ? "cmd" : "sh");

        assertThat(resolved).isNotEqualTo(Executables.isWindows() ? "cmd" : "sh");
        assertThat(new File(resolved)).exists();
    }

    @Test
    void keepsPathsThatAlreadyContainASeparator() {
        String path = Executables.isWindows() ? "C:\\nvm4w\\nodejs\\npm.cmd" : "/bin/sh";

        assertThat(Executables.resolve(path)).isEqualTo(path);
    }

    @Test
    void keepsUnknownNameAsIs() {
        String unknown = "definitely-not-installed-xyz";

        assertThat(Executables.resolve(unknown)).isEqualTo(unknown);
    }

    @Test
    void blankNameIsReturnedAsIs() {
        assertThat(Executables.resolve("  ")).isEqualTo("  ");
        assertThat(Executables.resolve(null)).isNull();
    }

    @Test
    void locateFindsKnownExecutableAndReturnsNullForUnknownName() {
        assertThat(Executables.locate(Executables.isWindows() ? "cmd" : "sh")).isNotNull();
        assertThat(Executables.locate("definitely-not-installed-xyz")).isNull();
    }

    @Test
    void locateReturnsNullWhenTheFileDoesNotExist() {
        String missing = Executables.isWindows() ? "C:\\definitely\\missing\\xyz.exe" : "/definitely/missing/xyz";

        assertThat(Executables.locate(missing)).isNull();
        assertThat(Executables.locate("  ")).isNull();
    }
}
