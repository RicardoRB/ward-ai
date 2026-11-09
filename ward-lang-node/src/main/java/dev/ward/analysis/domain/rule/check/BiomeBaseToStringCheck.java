package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Reports a value that is stringified while it only has the default object representation: {@code
 * String(value)}, {@code value.toString()} and {@code values.join()} over an array of objects. A
 * value whose type is unknown is never reported. shortcut: template interpolation is not checked,
 * and only the first argument of String is read
 */
public final class BiomeBaseToStringCheck implements RuleCheck {

  private static final String CALL = "singleExpression";
  private static final String TEMPLATE_ATOM = "templateStringAtom";
  private static final String STRING_FUNCTION = "String";
  private static final String TO_STRING = "toString";
  private static final String JOIN = ".join";
  private static final String ARGUMENTS = "arguments";
  private static final String ARGUMENT_LIST = "argumentList";
  private static final String ARGUMENT = "argument";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public BiomeBaseToStringCheck(final String message) {
    this.message = message;
  }

  @Override
  public FactNeed needs() {
    return new FactNeed(Set.of(CALL), EnumSet.of(FactKind.TYPE, FactKind.SHAPE, FactKind.MEMBERS));
  }

  /** Returns nothing: without type facts every value is unknown, so nothing is reported. */
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
    return SyntaxWalk.visits(root, Set.of(CALL, TEMPLATE_ATOM)).stream()
        .map(visit -> finding(visit.node(), types))
        .flatMap(Optional::stream)
        .toList();
  }

  private Optional<RuleMatch> finding(final SyntaxNode call, final UnitTypeFacts types) {
    if (TEMPLATE_ATOM.equals(call.kind())) {
      return substitution(call, types);
    }
    if (!hasArguments(call)) {
      return Optional.empty();
    }
    final String callee = SyntaxWalk.text(call.children().get(0));
    if (STRING_FUNCTION.equals(callee)) {
      return stringArgument(call)
          .filter(argument -> BiomeStringTypes.defaultObject(argument, types))
          .map(this::match);
    }
    if (callee.endsWith(JOIN) && BiomeStringTypes.objectArray(receiver(call), types)) {
      return Optional.of(match(call));
    }
    if (callee.endsWith("." + TO_STRING) && BiomeStringTypes.defaultObject(receiver(call), types)) {
      return Optional.of(match(call));
    }
    return Optional.empty();
  }

  /** A {@code ${value}} substitution in a template string, whose value is stringified. */
  private Optional<RuleMatch> substitution(final SyntaxNode atom, final UnitTypeFacts types) {
    return atom.children().stream()
        .filter(child -> BiomeTree.isKind(child, CALL))
        .findFirst()
        .filter(value -> BiomeStringTypes.defaultObject(value, types))
        .map(this::match);
  }

  private static boolean hasArguments(final SyntaxNode call) {
    return call.children().size() == 2 && BiomeTree.isKind(call.children().get(1), ARGUMENTS);
  }

  /** The object a dotted call is made on: the first child of its callee. */
  private static SyntaxNode receiver(final SyntaxNode call) {
    return call.children().get(0).children().get(0);
  }

  private static Optional<SyntaxNode> stringArgument(final SyntaxNode call) {
    return call.children().get(1).children().stream()
        .flatMap(BiomeBaseToStringCheck::argumentsOf)
        .filter(child -> BiomeTree.isKind(child, ARGUMENT))
        .flatMap(argument -> argument.children().stream())
        .findFirst();
  }

  /** The arguments of a call: a list under the parentheses in TypeScript, or none in JavaScript. */
  private static Stream<SyntaxNode> argumentsOf(final SyntaxNode child) {
    return BiomeTree.isKind(child, ARGUMENT_LIST) ? child.children().stream() : Stream.of(child);
  }

  private RuleMatch match(final SyntaxNode node) {
    return CheckSupport.at(node.span(), message);
  }
}
