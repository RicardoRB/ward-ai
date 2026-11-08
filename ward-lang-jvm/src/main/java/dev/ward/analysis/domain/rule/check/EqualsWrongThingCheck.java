package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports an {@code equals(Object)} method that compares a member of this object with a different
 * member of the other one, such as {@code Objects.equals(this.first, that.second)}, as Error
 * Prone's EqualsWrongThing does. A member is a field or a getter; the other object is any variable
 * other than {@code this}.
 */
public final class EqualsWrongThingCheck implements UnitCheck {

  private static final Pattern PAIR =
      Pattern.compile(
          "(?:(?:java\\.util\\.)?Objects\\.equals\\(((?:this\\.)?\\w+(?:\\(\\))?),(\\w+\\.\\w+(?:\\(\\))?)\\)"
              + "|((?:this\\.)?\\w+(?:\\(\\))?)\\.equals\\((\\w+\\.\\w+(?:\\(\\))?)\\))");
  private static final String THIS = "this.";
  private static final int OWN_OBJECTS = 1;
  private static final int OTHER_OBJECTS = 2;
  private static final int OWN_RECEIVER = 3;
  private static final int OTHER_RECEIVER = 4;

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the comparison; {@code {value}} is the other member
   */
  public EqualsWrongThingCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    return SyntaxWalk.visits(root, Set.of(MemberDeclarations.METHOD)).stream()
        .map(SyntaxWalk.Visit::node)
        .filter(EqualsWrongThingCheck::isEquals)
        .flatMap(method -> SyntaxWalk.visits(method, Set.of("expression")).stream())
        .map(SyntaxWalk.Visit::node)
        .flatMap(expression -> reportOf(expression).stream())
        .toList();
  }

  private static boolean isEquals(final SyntaxNode method) {
    return SyntaxWalk.text(method).contains("booleanequals(Object");
  }

  private Optional<RuleMatch> reportOf(final SyntaxNode expression) {
    final Matcher pair = PAIR.matcher(SyntaxWalk.text(expression));
    if (!pair.matches()) {
      return Optional.empty();
    }
    final String own =
        pair.group(OWN_OBJECTS) != null ? pair.group(OWN_OBJECTS) : pair.group(OWN_RECEIVER);
    final String other =
        pair.group(OTHER_OBJECTS) != null ? pair.group(OTHER_OBJECTS) : pair.group(OTHER_RECEIVER);
    final String otherMember = member(other.substring(other.indexOf('.') + 1));
    if (other.startsWith(THIS) || member(own.replace(THIS, "")).equals(otherMember)) {
      return Optional.empty();
    }
    return Optional.of(
        CheckSupport.at(expression.span(), CheckSupport.fill(message, otherMember, "")));
  }

  private static String member(final String text) {
    final String bare = text.replace("()", "");
    final String stripped = bare.replaceFirst("^(?:get|is)(?=[A-Z])", "");
    return stripped.isEmpty()
        ? bare
        : stripped.substring(0, 1).toLowerCase(Locale.ROOT) + stripped.substring(1);
  }
}
