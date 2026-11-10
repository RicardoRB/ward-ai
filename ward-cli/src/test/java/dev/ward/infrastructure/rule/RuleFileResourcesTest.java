package dev.ward.infrastructure.rule;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.RuleFileProvider;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Packaged rule files must exist on the classpath and be listed in order. Their native-image globs
 * are checked by {@code NativeResourceGlobsTest}, which covers every packaged resource.
 */
class RuleFileResourcesTest {

  @Test
  void everyRuleFileExistsOnTheClasspath() {
    for (final String file : bundledFiles()) {
      assertThat(ClasspathRuleSource.class.getResource("/" + file))
          .as("classpath resource %s", file)
          .isNotNull();
    }
  }

  private static List<String> bundledFiles() {
    return BundledLanguages.ruleFiles().stream()
        .flatMap(provider -> provider.ruleFiles().stream())
        .toList();
  }

  @Test
  void everyLanguageListsItsRuleFilesAlphabetically() {
    for (final RuleFileProvider provider : BundledLanguages.ruleFiles()) {
      assertThat(provider.ruleFiles()).isSorted();
    }
  }
}
