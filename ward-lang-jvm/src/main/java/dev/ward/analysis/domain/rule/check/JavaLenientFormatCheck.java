package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports a call to a lenient format method, such as {@code Preconditions.checkArgument} or {@code
 * Strings.lenientFormat}, whose literal template has a different number of {@code %s} placeholders
 * than the call has arguments, as Error Prone's LenientFormatStringValidation does. A template that
 * is not one plain literal, or an argument array, is not read.
 */
public final class JavaLenientFormatCheck implements RuleCheck {

  private static final Pattern LITERAL = Pattern.compile("^\"(?:[^\"\\\\]|\\\\.)*\"$");
  private static final String PLACEHOLDER = "%s";
  private static final String PRECONDITIONS = "com.google.common.base.Preconditions";
  private static final String VERIFY = "com.google.common.base.Verify";
  private static final Map<String, Integer> TEMPLATE_INDEX =
      Map.of(
          "com.google.common.base.Strings#lenientFormat",
          0,
          PRECONDITIONS + "#checkArgument",
          1,
          PRECONDITIONS + "#checkState",
          1,
          PRECONDITIONS + "#checkNotNull",
          1,
          VERIFY + "#verify",
          1,
          VERIFY + "#verifyNotNull",
          1);

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} is the number of placeholders and {@code {max}} the
   *     number of arguments
   */
  public JavaLenientFormatCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public FactNeed needs() {
    return JavaCalls.CALLS_AND_TYPES;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return List.of();
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root,
      final FlowView flow,
      final ScopeView scopes,
      final UnitTypeFacts types,
      final SourceUnit unit) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of("expression"))) {
      final SyntaxNode call = visit.node();
      final Optional<Integer> index = templateIndex(call, types);
      final List<SyntaxNode> arguments = JavaCalls.arguments(call);
      if (index.isPresent() && arguments.size() > index.get()) {
        mismatch(call, arguments, index.get(), types).ifPresent(matches::add);
      }
    }
    return List.copyOf(matches);
  }

  private static Optional<Integer> templateIndex(final SyntaxNode call, final UnitTypeFacts types) {
    return types.callTarget(call.span()).map(JavaLenientFormatCheck::key).map(TEMPLATE_INDEX::get);
  }

  private static String key(final CallTarget target) {
    return target.declaringType().name() + "#" + target.name();
  }

  private Optional<RuleMatch> mismatch(
      final SyntaxNode call,
      final List<SyntaxNode> arguments,
      final int index,
      final UnitTypeFacts types) {
    final String template = SyntaxWalk.text(arguments.get(index));
    final int values = arguments.size() - index - 1;
    final boolean array =
        values == 1
            && types
                .typeOf(arguments.get(arguments.size() - 1).span())
                .filter(type -> type.arrayDepth() > 0)
                .isPresent();
    if (!LITERAL.matcher(template).matches() || array) {
      return Optional.empty();
    }
    final int placeholders = template.split(Pattern.quote(PLACEHOLDER), -1).length - 1;
    return placeholders == values
        ? Optional.empty()
        : Optional.of(
            CheckSupport.at(
                call.span(),
                CheckSupport.fill(message, String.valueOf(placeholders), String.valueOf(values))));
  }
}
