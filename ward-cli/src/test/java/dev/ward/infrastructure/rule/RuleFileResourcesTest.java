package dev.ward.infrastructure.rule;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Every packaged rule file ships in the native image only when a registered resource glob matches
 * it. The JVM tests load the files from the classpath either way, so without this check a missing
 * glob shows up only in the native smoke test.
 */
class RuleFileResourcesTest {
  private static final Path METADATA =
      Path.of(
          "src/main/resources/META-INF/native-image/dev.ward/ward-cli/reachability-metadata.json");
  private static final String DOUBLE_STAR = "**";
  private static final Pattern GLOB = Pattern.compile("\"glob\":\\s*\"([^\"]+)\"");

  @Test
  void everyRuleFileMatchesANativeResourceGlob() throws IOException {
    final List<Pattern> globs = globs();

    for (final String file : bundledFiles()) {
      assertThat(globs)
          .as("native resource glob for %s", file)
          .anyMatch(glob -> glob.matcher(file).matches());
    }
  }

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
    for (final var provider : BundledLanguages.ruleFiles()) {
      assertThat(provider.ruleFiles()).isSorted();
    }
  }

  private static List<Pattern> globs() throws IOException {
    final Matcher matcher = GLOB.matcher(Files.readString(METADATA));
    return matcher.results().map(result -> toRegex(result.group(1))).toList();
  }

  /** Native-image globs: {@code **} spans folders, {@code *} stays inside one name. */
  private static Pattern toRegex(final String glob) {
    final StringBuilder regex = new StringBuilder();
    int index = 0;
    while (index < glob.length()) {
      if (glob.startsWith(DOUBLE_STAR, index)) {
        regex.append(".*");
        index += DOUBLE_STAR.length();
      } else {
        regex.append(
            glob.charAt(index) == '*' ? "[^/]*" : Pattern.quote(glob.substring(index, index + 1)));
        index++;
      }
    }
    return Pattern.compile(regex.toString());
  }
}
