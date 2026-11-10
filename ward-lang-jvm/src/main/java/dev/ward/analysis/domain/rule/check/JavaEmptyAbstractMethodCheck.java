package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports a method of an abstract class whose body is empty or only returns {@code null}, zero or
 * the empty string, as PMD's EmptyMethodInAbstractClassShouldBeAbstract does.
 */
public final class JavaEmptyAbstractMethodCheck implements RuleCheck {

  private static final String ABSTRACT = "abstract";
  private static final Pattern TRIVIAL = Pattern.compile("^\\{;*(?:return(?:null|0|\"\");)?;*}$");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public JavaEmptyAbstractMethodCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of("classDeclaration"))) {
      if (JavaNodes.modifiers(visit).contains(ABSTRACT)) {
        JavaNodes.members(visit.node()).stream()
            .filter(member -> "methodDeclaration".equals(member.node().kind()))
            .filter(member -> trivialBody(member.node()))
            .forEach(member -> matches.add(CheckSupport.at(member.node().span(), message)));
      }
    }
    return List.copyOf(matches);
  }

  private static boolean trivialBody(final SyntaxNode method) {
    return JavaNodes.kid(method, "methodBody")
        .flatMap(body -> JavaNodes.kid(body, "block"))
        .map(block -> TRIVIAL.matcher(SyntaxWalk.text(block)).matches())
        .orElse(false);
  }
}
