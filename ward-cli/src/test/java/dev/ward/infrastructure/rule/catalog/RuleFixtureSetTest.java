package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuleFixtureSetTest {

  @TempDir Path directory;

  @Test
  void typeScriptCaseUsesTsxWhenPresentAndTsOtherwise() throws IOException {
    Files.writeString(directory.resolve("violating.tsx"), "const a = <div />;\n");
    Files.writeString(directory.resolve("passing.ts"), "const b = 1;\n");
    final RuleFixtureSet set =
        new RuleFixtureSet(AnalysisLanguage.TYPESCRIPT, "rule", directory);

    assertThat(set.violating("")).isEqualTo(directory.resolve("violating.tsx"));
    assertThat(set.passing("")).isEqualTo(directory.resolve("passing.ts"));
  }

  @Test
  void javaScriptCaseIgnoresATsxFile() throws IOException {
    Files.writeString(directory.resolve("violating.tsx"), "const a = <div />;\n");
    final RuleFixtureSet set =
        new RuleFixtureSet(AnalysisLanguage.JAVASCRIPT, "rule", directory);

    assertThat(set.violating("")).isEqualTo(directory.resolve("violating.js"));
  }
}
