package dev.ward.analysis.domain.swift;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.ProjectMatch;
import dev.ward.analysis.domain.rule.check.ProjectCheck;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/** The whole-project Swift checks, over files that reference each other (#755, #756). */
class SwiftProjectChecksTest {
  private static final String MESSAGE = "finding";

  private static List<ProjectMatch> run(final ProjectCheck check, final Map<String, String> files) {
    final SortedMap<String, SyntaxNode> trees = new TreeMap<>();
    files.forEach(
        (path, text) ->
            trees.put(
                path,
                new AntlrLanguageFrontend()
                    .parse(new SourceUnit(path, AnalysisLanguage.SWIFT, text))
                    .tree()
            )
    );
    return check.project(trees);
  }

  @Test
  void aTestCaseInheritedInAnotherFileIsABaseClass() {
    final Map<String, String> files =
        Map.of("A.swift", "class Base: XCTestCase {}\n", "B.swift", "final class T: Base {}\n");

    assertThat(run(new SwiftFinalTestCaseCheck(MESSAGE), files)).isEmpty();
  }

  @Test
  void aTestCaseNobodyInheritsIsReportedInItsOwnFile() {
    final Map<String, String> files = Map.of(
        "A.swift",
        "class Leaf: XCTestCase {}\n",
        "B.swift",
        "class Other {}\n"
    );

    assertThat(run(new SwiftFinalTestCaseCheck(MESSAGE), files))
        .extracting(ProjectMatch::path)
        .containsExactly("A.swift");
  }

  @Test
  void anAsyncWitnessOfARequirementInAnotherFileIsKept() {
    final Map<String, String> files =
        Map.of(
            "P.swift",
            "protocol Loader { func load() async -> Int }\n",
            "S.swift",
            "struct S: Loader { func load() async -> Int { 1 } }\n"
        );

    assertThat(run(new SwiftAsyncWithoutAwaitCheck(MESSAGE), files)).isEmpty();
  }

  @Test
  void aSameNamedMethodOfATypeThatDoesNotConformIsReported() {
    final Map<String, String> files =
        Map.of(
            "P.swift",
            "protocol Loader { func load() async -> Int }\n",
            "S.swift",
            "struct S { func load() async -> Int { 1 } }\n"
        );

    assertThat(run(new SwiftAsyncWithoutAwaitCheck(MESSAGE), files)).hasSize(1);
  }
}
