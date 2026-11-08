package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports a security or privilege check made inside a method that a subclass can override, as
 * SpotBugs' VSC_VULNERABLE_SECURITY_CHECK_METHODS does. The method is neither private, final nor
 * static and its class is not final.
 */
public final class JavaOverridableSecurityCheck implements RuleCheck {

  private static final Pattern CHECK =
      Pattern.compile(
          "System\\.getSecurityManager\\(\\)\\.check\\w+\\(|AccessController\\.checkPermission\\(");
  private static final Set<String> FIXED = Set.of("private", "final", "static");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} is the method name
   */
  public JavaOverridableSecurityCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(MemberDeclarations.CLASS))) {
      if (!JavaNodes.modifiers(visit).contains(MemberDeclarations.FINAL)) {
        ClassShapes.methods(visit.node()).stream()
            .filter(JavaOverridableSecurityCheck::overridable)
            .filter(member -> CHECK.matcher(SyntaxWalk.text(member)).find())
            .forEach(
                member ->
                    matches.add(
                        CheckSupport.at(
                            member.span(),
                            CheckSupport.fill(
                                message,
                                MemberDeclarations.name(ClassMembers.declarationOf(member)),
                                ""))));
      }
    }
    return List.copyOf(matches);
  }

  private static boolean overridable(final SyntaxNode member) {
    return MemberDeclarations.modifiers(member).stream().noneMatch(FIXED::contains);
  }
}
