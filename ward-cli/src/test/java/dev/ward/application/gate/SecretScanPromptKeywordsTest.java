package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.TextFile;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.infrastructure.rule.ClasspathRuleSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Leaving out rules by keyword must never leave out a rule that matches its own fixture. */
class SecretScanPromptKeywordsTest {
  private static final int MIN_CHECKED = 150;
  private static final int QUARTER = 4;
  private static final Path FIXTURES = Path.of("src/test/resources/rules/secrets");

  @Test
  void everyRuleStaysForTheViolatingFixtureOfItsOwn() throws IOException {
    final List<RuleDefinition> rules = new ClasspathRuleSource().loadSecrets().rules();
    int checked = 0;
    try (Stream<Path> dirs = Files.list(FIXTURES)) {
      for (final Path dir : dirs.toList()) {
        final String id = "ward:secrets/" + dir.getFileName();
        final Optional<RuleDefinition> rule =
            rules.stream().filter(candidate -> candidate.id().equals(id)).findFirst();
        final Path violating = firstViolating(dir);
        if (rule.isPresent() && violating != null) {
          assertThat(relevantFor(rule.get(), violating)).as(id).containsExactly(rule.get());
          checked++;
        }
      }
    }
    assertThat(checked).isGreaterThan(MIN_CHECKED);
  }

  @Test
  void aTextWithoutAKeywordLeavesTheRuleOut() {
    final List<RuleDefinition> rules = new ClasspathRuleSource().loadSecrets().rules();

    assertThat(PromptKeywords.relevant(rules, List.of(new TextFile("", "Rename the class Foo."))))
        .hasSizeLessThan(rules.size() / QUARTER);
  }

  @Test
  void unreadableOrMissingKeywordsKeepTheRule() {
    assertThat(PromptKeywords.keywords("regex = 'x'")).isEmpty();
    assertThat(PromptKeywords.keywords("keywords = [\"a\\nb\"]")).isEmpty();
    assertThat(PromptKeywords.keywords("keywords = ['ab', \"cd\"]")).contains(List.of("ab", "cd"));
  }

  private static List<RuleDefinition> relevantFor(final RuleDefinition rule, final Path violating)
      throws IOException {
    return PromptKeywords.relevant(
        List.of(rule),
        List.of(new TextFile("f", Files.readString(violating)))
    );
  }

  private static Path firstViolating(final Path dir) throws IOException {
    try (Stream<Path> files = Files.list(dir)) {
      return files.filter(
          file -> file.getFileName().toString().startsWith("violating")
      ).findFirst().orElse(null);
    }
  }
}
