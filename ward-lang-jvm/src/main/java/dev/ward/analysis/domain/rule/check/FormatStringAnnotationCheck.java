package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

/**
 * Reports a computed string passed for a parameter annotated {@code @FormatString}, as Error
 * Prone's FormatStringAnnotation does: the format must be a compile-time constant. An argument that
 * is a literal, a constant, or a plain name (which may itself be a {@code @FormatString} parameter)
 * is not reported, nor is a call whose target is unknown.
 */
public final class FormatStringAnnotationCheck extends FactsCheck {

  private static final String ANNOTATION = "com.google.errorprone.annotations.FormatString";
  private static final Pattern LITERAL = Pattern.compile("^\"(?:[^\"\\\\]|\\\\.)*\"$");
  private static final Pattern COMPUTED = Pattern.compile("[+(]");
  private static final String EXPRESSION = "expression";
  private static final FactNeed NEED =
      new FactNeed(Set.of(EXPRESSION), Set.of(FactKind.CALL, FactKind.CONSTANT));

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the argument
   */
  public FormatStringAnnotationCheck(final String message) {
    super(NEED);
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  List<RuleMatch> check(final SyntaxNode root, final UnitTypeFacts types) {
    return SyntaxWalk.visits(root, Set.of(EXPRESSION)).stream()
        .map(SyntaxWalk.Visit::node)
        .flatMap(call -> reportsOf(call, types).stream())
        .toList();
  }

  private List<RuleMatch> reportsOf(final SyntaxNode call, final UnitTypeFacts types) {
    final Optional<CallTarget> target = types.callTarget(call.span());
    final List<SyntaxNode> arguments = CallArgumentNodes.all(call);
    if (target.isEmpty()) {
      return List.of();
    }
    final List<List<String>> annotated = target.get().parameterAnnotations();
    return IntStream.range(0, Math.min(arguments.size(), annotated.size()))
        .filter(index -> annotated.get(index).contains(ANNOTATION))
        .mapToObj(arguments::get)
        .filter(argument -> computed(argument, types))
        .map(argument -> CheckSupport.at(argument.span(), message))
        .toList();
  }

  private static boolean computed(final SyntaxNode argument, final UnitTypeFacts types) {
    final String text = SyntaxWalk.text(argument);
    return !LITERAL.matcher(text).matches()
        && COMPUTED.matcher(text).find()
        && types.constant(argument.span()).isEmpty();
  }
}
