package dev.ward.analysis.domain.rule.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.ProjectFiles;
import dev.ward.analysis.domain.rule.ProjectMatch;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.ir.Span;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/** The default overload of a project check, which ignores the manifest files. */
final class ProjectCheckTest {

  private static final Span AT = new Span(1, 1, 1, 2, 0, 1);

  @Test
  void defaultOverloadDelegatesToTheTreesOnlyMethod() {
    final ProjectCheck check =
        trees -> List.of(new ProjectMatch("a.ts", new RuleMatch(AT, "found " + trees.size())));
    final SortedMap<String, SyntaxNode> trees = new TreeMap<>();
    trees.put("a.ts", new SyntaxNode("file", AT, Map.of(), List.of()));

    assertThat(check.project(trees, new ProjectFiles(Map.of("package.json", "{}"))))
        .containsExactly(new ProjectMatch("a.ts", new RuleMatch(AT, "found 1")));
  }
}
