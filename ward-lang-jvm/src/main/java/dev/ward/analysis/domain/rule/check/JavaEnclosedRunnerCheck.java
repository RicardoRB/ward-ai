package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports a {@code @Test} method declared directly in a class run with JUnit's {@code Enclosed}
 * runner, which runs only the tests of the nested classes, as Error Prone's
 * JUnit4TestsNotRunWithinEnclosed does.
 */
public final class JavaEnclosedRunnerCheck implements RuleCheck {

  private static final Pattern ENCLOSED =
      Pattern.compile("^@(?:\\w+\\.)*RunWith\\(.*Enclosed\\.class\\)$");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} is the method name
   */
  public JavaEnclosedRunnerCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of("classDeclaration"))) {
      final boolean enclosed =
          JavaNodes.modifiers(visit).stream().anyMatch(text -> ENCLOSED.matcher(text).matches());
      if (enclosed) {
        JavaNodes.members(visit.node()).stream()
            .filter(member -> "methodDeclaration".equals(member.node().kind()))
            .filter(member -> JUnitMembers.annotations(member.holder()).contains("Test"))
            .forEach(
                member ->
                    matches.add(
                        CheckSupport.at(
                            member.node().span(),
                            CheckSupport.fill(message, JavaNodes.name(member.node()), ""))));
      }
    }
    return List.copyOf(matches);
  }
}
