package dev.ward.analysis.application.node;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** The rule files {@link NodeRuleFiles} lists exist, and their rule ids are unique. */
class NodeRuleFilesTest {

  private static final String CHECKSTYLE_FILE = "rules/node/naming.toml";
  private static final Pattern ID = Pattern.compile("^id = \"ward:(\\w+)/([\\w-]+)\"$");
  private static final String TYPESCRIPT = "typescript";
  private static final String JAVASCRIPT = "javascript";

  @Test
  void everyListedFileIsOnTheClasspath() {
    final List<String> files = new NodeRuleFiles().ruleFiles();

    assertThat(files).contains(CHECKSTYLE_FILE).isSorted();
    for (final String file : files) {
      assertThat(NodeRuleFilesTest.class.getResource("/" + file)).as(file).isNotNull();
    }
  }

  @Test
  void ruleIdsAreUniqueAcrossTheFiles() throws IOException {
    final Set<String> seen = new HashSet<>();
    for (final String file : new NodeRuleFiles().ruleFiles()) {
      for (final String id : ids(file)) {
        assertThat(seen.add(id)).as("duplicate id " + id).isTrue();
      }
    }
  }

  @Test
  void checkstyleRulesComeInTypeScriptAndJavaScriptPairs() throws IOException {
    final List<String> ids = ids(CHECKSTYLE_FILE);

    assertThat(ids).isNotEmpty();
    for (final String id : ids) {
      final String twin =
          id.startsWith("ward:" + TYPESCRIPT)
              ? id.replace(TYPESCRIPT, JAVASCRIPT)
              : id.replace(JAVASCRIPT, TYPESCRIPT);
      assertThat(ids).as("twin of " + id).contains(twin);
    }
  }

  private static List<String> ids(final String file) throws IOException {
    try (InputStream in = NodeRuleFilesTest.class.getResourceAsStream("/" + file)) {
      assertThat(in).as(file).isNotNull();
      final String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      return text.lines()
          .map(ID::matcher)
          .filter(Matcher::matches)
          .map(matcher -> "ward:" + matcher.group(1) + "/" + matcher.group(2))
          .toList();
    }
  }
}
