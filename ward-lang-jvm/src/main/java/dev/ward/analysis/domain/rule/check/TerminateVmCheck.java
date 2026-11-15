package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports a call that ends the JVM, {@code System.exit}, {@code System.halt} or the same on {@code
 * Runtime}, except inside a method named {@code main}, as PMD's DoNotTerminateVM allows.
 */
public final class TerminateVmCheck implements UnitCheck {
  private static final Pattern TERMINATE =
      Pattern.compile(
          "^(?:(?:java\\.lang\\.)?System|Runtime\\.getRuntime\\(\\))\\.(?:exit|halt)\\("
      );
  private static final Set<String> METHODS = Set.of("methodDeclaration");
  private static final String MAIN = "main";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the call
   */
  public TerminateVmCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    return SyntaxWalk.visits(root, Set.of("expression")).stream()
        .filter(visit -> TERMINATE.matcher(SyntaxWalk.text(visit.node())).find())
        .filter(visit -> !inMain(visit.ancestors()))
        .map(visit -> CheckSupport.at(visit.node().span(), message))
        .toList();
  }

  private static boolean inMain(final List<SyntaxNode> up) {
    for (int index = up.size() - 1; index >= 0; index--) {
      final SyntaxNode node = up.get(index);
      if (METHODS.contains(node.kind())) {
        return node.children().stream()
            .anyMatch(
                child -> "identifier".equals(child.kind()) && MAIN.equals(SyntaxWalk.text(child))
            );
      }
    }
    return false;
  }
}
