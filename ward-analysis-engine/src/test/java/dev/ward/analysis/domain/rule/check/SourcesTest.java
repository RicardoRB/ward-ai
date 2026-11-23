package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Which files each {@link Sources} mode looks at. */
class SourcesTest {
  @Test
  void appSkipsToolConfigurationAndTestFiles() {
    assertThat(Sources.APP.admits("src/index.ts")).isTrue();
    assertThat(Sources.APP.admits("vitest.config.mjs")).isFalse();
    assertThat(Sources.APP.admits("eslint.config.js")).isFalse();
    assertThat(Sources.APP.admits("packages/web/jest.config.ts")).isFalse();
    assertThat(Sources.APP.admits("src/index.test.ts")).isFalse();
  }

  @Test
  void mainAndTestStillSplitOnTestPaths() {
    assertThat(Sources.MAIN.admits("vitest.config.mjs")).isTrue();
    assertThat(Sources.MAIN.admits("src/a.spec.ts")).isFalse();
    assertThat(Sources.TEST.admits("src/a.spec.ts")).isTrue();
    assertThat(Sources.ALL.admits("anything.ts")).isTrue();
  }

  @Test
  void pythonTestHelpersAndSeedScriptsAreNotMainSources() {
    assertThat(Sources.MAIN.admits("tests/helpers.py")).isFalse();
    assertThat(Sources.MAIN.admits("populate.py")).isFalse();
    assertThat(Sources.MAIN.admits("scripts/seed_data.py")).isFalse();
    assertThat(Sources.MAIN.admits("app/seeding.py")).isTrue();
    assertThat(Sources.MAIN.admits("app/views.py")).isTrue();
  }

  @Test
  void parseReadsAppAndRejectsUnknownValues() {
    assertThat(Sources.parse("app")).isEqualTo(Sources.APP);
    assertThatThrownBy(() -> Sources.parse("none")).hasMessageContaining("app");
  }
}
