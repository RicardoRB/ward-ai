package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Reports each increment, decrement or compound assignment of a volatile field of the same file. A
 * volatile field does not make {@code x++} atomic.
 */
public final class VolatileIncrementCheck implements UnitCheck {

  private static final String MODIFIER = "modifier";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} is the field name
   */
  public VolatileIncrementCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final Set<String> names = volatileNames(root);
    if (names.isEmpty()) {
      return List.of();
    }
    final String alternatives = names.stream().map(Pattern::quote).collect(Collectors.joining("|"));
    final Pattern update =
        Pattern.compile(
            "^(?:(?:this\\.)?(?:"
                + alternatives
                + ")(?:\\+\\+|--|[-+*/]=)|(?:\\+\\+|--)(?:this\\.)?"
                + "(?:"
                + alternatives
                + "))$");
    return SyntaxWalk.descendants(root).stream()
        .filter(node -> "expression".equals(node.kind()))
        .filter(node -> update.matcher(SyntaxWalk.text(node)).matches())
        .map(node -> CheckSupport.at(node.span(), message))
        .toList();
  }

  private static Set<String> volatileNames(final SyntaxNode root) {
    return SyntaxWalk.visits(root, Set.of("classBody")).stream()
        .flatMap(visit -> JUnitMembers.members(visit.node()).stream())
        .filter(VolatileIncrementCheck::isVolatile)
        .flatMap(member -> SyntaxWalk.descendants(member).stream())
        .filter(node -> "variableDeclaratorId".equals(node.kind()))
        .map(SyntaxWalk::text)
        .collect(Collectors.toUnmodifiableSet());
  }

  private static boolean isVolatile(final SyntaxNode member) {
    return member.children().stream()
        .anyMatch(c -> MODIFIER.equals(c.kind()) && "volatile".equals(SyntaxWalk.text(c)));
  }
}
