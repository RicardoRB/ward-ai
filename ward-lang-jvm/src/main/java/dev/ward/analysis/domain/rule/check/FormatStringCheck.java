package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports a call of a formatting method whose format string is a literal that does not fit the
 * arguments: an unknown conversion, too few or too many arguments, or an argument of a type the
 * conversion rejects, as Error Prone's FormatString and FloggerFormatString do. The methods are
 * named by their declaring type and name. A call whose target is unknown, whose format is not a
 * literal, or whose only argument is an array is never reported.
 */
public final class FormatStringCheck extends FactsCheck {

  private static final Pattern LITERAL = Pattern.compile("^\"((?:[^\"\\\\]|\\\\.)*)\"$");
  private static final String EXPRESSION = "expression";
  private static final int FORMAT_POSITIONS = 2;
  private static final FactNeed NEED =
      new FactNeed(Set.of(EXPRESSION), Set.of(FactKind.CALL, FactKind.TYPE));

  private final Set<String> declaringTypes;
  private final Set<String> names;
  private final String message;

  /**
   * Creates the check.
   *
   * @param declaringTypes qualified names of the types that declare the formatting methods
   * @param names names of the formatting methods
   * @param message the message reported at the call; {@code {value}} is the fault
   */
  public FormatStringCheck(
      final Set<String> declaringTypes, final Set<String> names, final String message) {
    super(NEED);
    this.declaringTypes = Set.copyOf(declaringTypes);
    this.names = Set.copyOf(names);
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  List<RuleMatch> check(final SyntaxNode root, final UnitTypeFacts types) {
    return SyntaxWalk.visits(root, Set.of(EXPRESSION)).stream()
        .map(SyntaxWalk.Visit::node)
        .flatMap(call -> reportOf(call, types).stream())
        .toList();
  }

  private Optional<RuleMatch> reportOf(final SyntaxNode call, final UnitTypeFacts types) {
    final Optional<CallTarget> target = types.callTarget(call.span());
    if (target.isEmpty()
        || !declaringTypes.contains(target.get().declaringType().name())
        || !names.contains(target.get().name())) {
      return Optional.empty();
    }
    final List<SyntaxNode> arguments = CallArgumentNodes.all(call);
    final int format = formatIndex(arguments);
    if (format < 0) {
      return Optional.empty();
    }
    final List<SyntaxNode> values = arguments.subList(format + 1, arguments.size());
    final List<Optional<TypeRef>> valueTypes =
        values.stream().map(value -> types.typeOf(value.span())).toList();
    if (valueTypes.size() == 1
        && valueTypes.get(0).filter(type -> type.arrayDepth() > 0).isPresent()) {
      return Optional.empty();
    }
    final Matcher literal = LITERAL.matcher(SyntaxWalk.text(arguments.get(format)));
    literal.matches();
    return FormatSpecifiers.fault(literal.group(1), valueTypes)
        .map(fault -> CheckSupport.at(call.span(), CheckSupport.fill(message, fault, "")));
  }

  private static int formatIndex(final List<SyntaxNode> arguments) {
    for (int index = 0; index < Math.min(FORMAT_POSITIONS, arguments.size()); index++) {
      if (LITERAL.matcher(SyntaxWalk.text(arguments.get(index))).matches()) {
        return index;
      }
    }
    return -1;
  }
}
