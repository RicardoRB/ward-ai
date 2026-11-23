package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.ProjectMatch;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.check.ProjectCheck;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;

/**
 * Reports a class that inherits {@code XCTestCase} directly, is not final, and has no subclass in
 * any file of the project, like SwiftLint's {@code final_test_case}. A class that another class
 * inherits from is a base test case and cannot be final.
 */
public final class SwiftFinalTestCaseCheck implements RuleCheck, ProjectCheck {
  private static final String CLASS = "class_declaration";
  private static final String TEST_CASE = "XCTestCase";
  private static final String FINAL = "final";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public SwiftFinalTestCaseCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return List.of();
  }

  @Override
  public List<ProjectMatch> project(final SortedMap<String, SyntaxNode> trees) {
    final Set<String> inheritedAnywhere = new HashSet<>();
    trees.values().forEach(
        tree -> classes(tree).forEach(c -> inheritedAnywhere.addAll(SwiftInheritance.inherited(c)))
    );
    return trees.entrySet().stream()
        .flatMap(
            entry ->
                classes(entry.getValue()).stream()
                    .filter(declaration -> reportable(declaration, inheritedAnywhere))
                    .map(
                        declaration -> new ProjectMatch(
                            entry.getKey(),
                            SwiftTrees.at(declaration, message)
                        )
                    )
        )
        .toList();
  }

  private static List<SyntaxNode> classes(final SyntaxNode tree) {
    return SwiftTrees.descendants(tree, CLASS);
  }

  private static boolean reportable(final SyntaxNode declaration, final Set<String> inherited) {
    final boolean isFinal =
        declaration.children().stream()
            .anyMatch(child -> FINAL.equals(child.fields().get("text")));
    return !isFinal
        && SwiftInheritance.inherited(declaration).contains(TEST_CASE)
        && SwiftInheritance.nameOf(declaration).filter(
            name -> !inherited.contains(name)
        ).isPresent();
  }
}
