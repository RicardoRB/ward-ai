package dev.ward.application.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.project.Toolchain;
import java.util.Arrays;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Every toolchain is either configured by init or listed here as unsupported, so a new toolchain
 * constant cannot be added without a support or an explicit decision.
 */
class InitToolchainsCoverageTest {
  private static final Set<Toolchain> UNSUPPORTED =
      Set.of(Toolchain.GO, Toolchain.RUST, Toolchain.DOTNET);

  @Test
  void everyToolchainHasASupportOrIsListedAsUnsupported() {
    for (final Toolchain toolchain : Toolchain.values()) {
      assertThat(InitToolchains.find(toolchain).isPresent() || UNSUPPORTED.contains(toolchain))
          .as("toolchain %s has no init support and is not listed as unsupported", toolchain)
          .isTrue();
    }
  }

  @Test
  void theUnsupportedListHasNoSupportOfItsOwn() {
    for (final Toolchain toolchain : UNSUPPORTED) {
      assertThat(InitToolchains.find(toolchain)).as("support for %s", toolchain).isEmpty();
    }
  }

  @Test
  void theRegistryHoldsOneSupportPerConfiguredToolchainAndEachNamesAPreset() {
    final long configured =
        Arrays.stream(Toolchain.values())
            .filter(toolchain -> !UNSUPPORTED.contains(toolchain))
            .count();

    assertThat(InitToolchains.all())
        .hasSize((int) configured)
        .allSatisfy(support -> assertThat(support.presets()).isNotEmpty());
  }
}
