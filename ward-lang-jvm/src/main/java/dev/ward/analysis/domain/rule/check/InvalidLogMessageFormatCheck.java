package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.TypeMembers;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports a call of an SLF4J or Log4j 2 logging method whose message is a string literal with a
 * number of {@code {}} placeholders that differs from its number of arguments, as PMD's
 * InvalidLogMessageFormat does. One trailing {@code Throwable} may follow the arguments. A call
 * that passes an array or builds its message is not judged; the {@code %s} format specifiers PMD
 * also counts are not.
 */
public final class InvalidLogMessageFormatCheck extends FactsCheck {

  private static final Set<String> LOGGERS =
      Set.of("org.slf4j.Logger", "org.apache.logging.log4j.Logger");
  private static final Set<String> LEVELS =
      Set.of("trace", "debug", "info", "warn", "error", "fatal");
  private static final Pattern LITERAL = Pattern.compile("^\"(?:[^\"\\\\]|\\\\.)*\"$");
  private static final Pattern PLACEHOLDER = Pattern.compile("(?<!\\\\)\\{}");
  private static final String THROWABLE = "java.lang.Throwable";
  private static final String EXPRESSION = "expression";
  private static final FactNeed NEED =
      new FactNeed(Set.of(EXPRESSION), Set.of(FactKind.CALL, FactKind.TYPE, FactKind.MEMBERS));

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the call; {@code {value}} is the counts
   */
  public InvalidLogMessageFormatCheck(final String message) {
    super(NEED);
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  List<RuleMatch> check(final SyntaxNode root, final UnitTypeFacts types) {
    return SyntaxWalk.visits(root, Set.of(EXPRESSION)).stream()
        .map(SyntaxWalk.Visit::node)
        .filter(
            call ->
                types
                    .callTarget(call.span())
                    .filter(InvalidLogMessageFormatCheck::isLog)
                    .isPresent())
        .flatMap(call -> reportOf(call, types).stream())
        .toList();
  }

  private static boolean isLog(final CallTarget target) {
    return LOGGERS.contains(target.declaringType().name()) && LEVELS.contains(target.name());
  }

  private List<RuleMatch> reportOf(final SyntaxNode call, final UnitTypeFacts types) {
    final List<SyntaxNode> arguments = CallArgumentNodes.all(call);
    final int at = messageIndex(arguments);
    if (at < 0) {
      return List.of();
    }
    final int placeholders =
        (int) PLACEHOLDER.matcher(SyntaxWalk.text(arguments.get(at))).results().count();
    final List<SyntaxNode> values = arguments.subList(at + 1, arguments.size());
    if (hasArray(values, types) || fits(placeholders, values, types)) {
      return List.of();
    }
    final String counts = placeholders + " placeholders, " + values.size() + " arguments";
    return List.of(CheckSupport.at(call.span(), CheckSupport.fill(message, counts, "")));
  }

  /** Returns the index of the message, the first or the second argument, or -1 for none. */
  private int messageIndex(final List<SyntaxNode> arguments) {
    for (int index = 0; index < Math.min(2, arguments.size()); index++) {
      if (isLiteral(SyntaxWalk.text(arguments.get(index)))) {
        return index;
      }
    }
    return -1;
  }

  private boolean isLiteral(final String text) {
    return LITERAL.matcher(text).matches();
  }

  private static boolean hasArray(final List<SyntaxNode> values, final UnitTypeFacts types) {
    return values.stream()
        .anyMatch(value -> types.typeOf(value.span()).filter(t -> t.arrayDepth() > 0).isPresent());
  }

  private static boolean fits(
      final int placeholders, final List<SyntaxNode> values, final UnitTypeFacts types) {
    return values.size() == placeholders
        || values.size() == placeholders + 1 && isThrowable(values.get(values.size() - 1), types);
  }

  private static boolean isThrowable(final SyntaxNode value, final UnitTypeFacts types) {
    final boolean named =
        types.typeOf(value.span()).map(TypeRef::name).filter(THROWABLE::equals).isPresent();
    return named
        || types.membersOf(value.span()).map(TypeMembers::ancestors).stream()
            .flatMap(List::stream)
            .anyMatch(ancestor -> THROWABLE.equals(ancestor.name()));
  }
}
