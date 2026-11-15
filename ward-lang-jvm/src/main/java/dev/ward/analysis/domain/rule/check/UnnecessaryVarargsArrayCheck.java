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
 * Reports an array with an initializer that is the last argument of a call of a varargs method and
 * has the array type of the variadic parameter, as PMD's UnnecessaryVarargsArrayCreation does: the
 * elements can be passed one by one. A call that does not resolve, a method that is not varargs,
 * and an array of a primitive for a parameter of another element type are skipped.
 */
public final class UnnecessaryVarargsArrayCheck extends FactsCheck {

  private static final String EXPRESSION = "expression";
  private static final String CREATOR = "creator";
  private static final Pattern ARRAY = Pattern.compile("^new([^\\[(]+)((?:\\[\\])+)\\{");
  private static final Set<String> PRIMITIVES =
      Set.of("boolean", "byte", "char", "short", "int", "long", "float", "double");
  private static final FactNeed NEED = new FactNeed(Set.of(EXPRESSION), Set.of(FactKind.CALL));

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the array creation
   */
  public UnnecessaryVarargsArrayCheck(final String message) {
    super(NEED);
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  List<RuleMatch> check(final SyntaxNode root, final UnitTypeFacts types) {
    return SyntaxWalk.visits(root, Set.of(EXPRESSION)).stream()
        .map(SyntaxWalk.Visit::node)
        .flatMap(call -> reportOf(call, types).stream())
        .toList();
  }

  private List<RuleMatch> reportOf(final SyntaxNode call, final UnitTypeFacts types) {
    final Optional<CallTarget> target = types.callTarget(call.span()).filter(CallTarget::isVarargs);
    final List<SyntaxNode> arguments = CallArgumentNodes.all(call);
    if (target.isEmpty() || arguments.size() != target.get().parameters().size()) {
      return List.of();
    }
    final TypeRef expected = target.get().parameters().get(arguments.size() - 1);
    final SyntaxNode last = arguments.get(arguments.size() - 1);
    final Matcher array = ARRAY.matcher(SyntaxWalk.text(last));
    if (!array.find() || !sameArray(array, expected)) {
      return List.of();
    }
    return last.children().stream()
        .filter(child -> CREATOR.equals(child.kind()))
        .map(creator -> CheckSupport.at(creator.span(), message))
        .toList();
  }

  /** Whether the created array has the type of the parameter, a primitive element type exactly. */
  private static boolean sameArray(final Matcher array, final TypeRef expected) {
    final String element = array.group(1);
    final int depth = array.group(2).length() / "[]".length();
    return depth == expected.arrayDepth()
        && (!PRIMITIVES.contains(element) || element.equals(expected.name()));
  }
}
