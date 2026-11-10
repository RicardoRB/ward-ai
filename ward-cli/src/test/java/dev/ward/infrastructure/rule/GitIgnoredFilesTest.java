package dev.ward.infrastructure.rule;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

/** An ignored folder covers every path under it, and nothing that only shares its prefix. */
class GitIgnoredFilesTest {

  private static final Set<String> IGNORED = Set.of("coverage/", "src/gen.ts");

  @Test
  void coversAnIgnoredFileAndEveryPathUnderAnIgnoredFolder() {
    assertThat(GitIgnoredFiles.covers(IGNORED, "src/gen.ts")).isTrue();
    assertThat(GitIgnoredFiles.covers(IGNORED, "coverage/lcov-report/index.js")).isTrue();
  }

  @Test
  void doesNotCoverAPathThatOnlySharesAFolderPrefix() {
    assertThat(GitIgnoredFiles.covers(IGNORED, "coverage-old/index.js")).isFalse();
    assertThat(GitIgnoredFiles.covers(IGNORED, "src/index.ts")).isFalse();
  }
}
