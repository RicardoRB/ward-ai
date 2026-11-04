package dev.ward.infrastructure.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.project.Toolchain;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Checks that every toolchain is classified, so a new one fails the build until it is. */
class ProjectDependencyAdaptersTest {

  @Test
  void everyToolchainHasLayoutReaderOrNoSupport() {
    final Set<Toolchain> classified = EnumSet.copyOf(ProjectDependencyAdapters.WITH_LAYOUT_READER);
    classified.addAll(ProjectDependencyAdapters.WITHOUT_SUPPORT);

    assertThat(classified).containsExactlyInAnyOrder(Toolchain.values());
  }

  @Test
  void aToolchainIsClassifiedOnlyOnce() {
    assertThat(ProjectDependencyAdapters.WITH_LAYOUT_READER)
        .doesNotContainAnyElementsOf(ProjectDependencyAdapters.WITHOUT_SUPPORT);
  }
}
