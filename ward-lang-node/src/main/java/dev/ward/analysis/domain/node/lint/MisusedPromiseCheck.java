package dev.ward.analysis.domain.node.lint;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports a promise where a value is expected: an {@code if} condition, an object spread, or a
 * promise-returning function passed where a void return is expected.
 */
public final class MisusedPromiseCheck extends TypedCheck {

  private static final String IF = "ifStatement";
  private static final String PROPERTY = "propertyAssignment";
  private static final String ARGUMENT = "argument";
  private static final String CALL_ARGUMENTS = "arguments";
  private static final String ARGUMENT_LIST = "argumentList";
  private static final String DECLARATION = "variableDeclaration";
  private static final String SPREAD = "...";
  private static final Pattern PROMISE_RESULT = Pattern.compile("=>\\s*Promise\\b");
  private static final Pattern VOID_RESULT = Pattern.compile("=>\\s*void$");

  private final boolean checksConditionals;
  private final boolean checksSpreads;
  private final boolean checksVoidReturn;
  private final String message;

  /** Creates the check with the rule's options and message. */
  public MisusedPromiseCheck(
      final boolean checksConditionals,
      final boolean checksSpreads,
      final boolean checksVoidReturn,
      final String message) {
    super();
    this.checksConditionals = checksConditionals;
    this.checksSpreads = checksSpreads;
    this.checksVoidReturn = checksVoidReturn;
    this.message = message;
  }

  @Override
  protected Set<FactKind> kinds() {
    return checksVoidReturn ? Set.of(FactKind.TYPE, FactKind.CALL) : super.kinds();
  }

  @Override
  protected List<RuleMatch> find(final SyntaxNode root, final UnitTypeFacts types) {
    final List<RuleMatch> out = new ArrayList<>();
    if (checksConditionals) {
      conditions(root, types, out);
    }
    if (checksSpreads) {
      spreads(root, types, out);
    }
    if (checksVoidReturn) {
      voidReturns(root, types, out);
      variables(root, types, out);
    }
    return out;
  }

  private void conditions(
      final SyntaxNode root, final UnitTypeFacts types, final List<RuleMatch> out) {
    for (final SyntaxNode statement : SyntaxText.nodesOf(root, IF)) {
      for (final SyntaxNode condition : SyntaxText.childrenOf(statement, SEQUENCE)) {
        if (isPromise(types, condition)) {
          out.add(new RuleMatch(condition.span(), message));
        }
      }
    }
  }

  private void spreads(
      final SyntaxNode root, final UnitTypeFacts types, final List<RuleMatch> out) {
    for (final SyntaxNode property : SyntaxText.nodesOf(root, PROPERTY)) {
      final SyntaxNode operand = SyntaxText.last(property);
      if (SyntaxText.hasToken(property, SPREAD)
          && EXPRESSION.equals(operand.kind())
          && isPromise(types, operand)) {
        out.add(new RuleMatch(operand.span(), message));
      }
    }
  }

  /** Reports a promise-returning value stored in a variable whose declared type returns void. */
  private void variables(
      final SyntaxNode root, final UnitTypeFacts types, final List<RuleMatch> out) {
    for (final SyntaxNode declaration : SyntaxText.nodesOf(root, DECLARATION)) {
      final SyntaxNode value = SyntaxText.last(declaration);
      final boolean typedAsVoid =
          typeOf(types, declaration).filter(MisusedPromiseCheck::returnsVoid).isPresent();
      if (value != null
          && EXPRESSION.equals(value.kind())
          && typedAsVoid
          && returns(types, value, PROMISE_RESULT)) {
        out.add(new RuleMatch(value.span(), message));
      }
    }
  }

  /** Reports a promise-returning function argument whose parameter is typed as returning void. */
  private void voidReturns(
      final SyntaxNode root, final UnitTypeFacts types, final List<RuleMatch> out) {
    for (final SyntaxNode call : SyntaxText.nodesOf(root, EXPRESSION)) {
      final List<SyntaxNode> parts = call.children();
      if (parts.size() == SyntaxText.INFIX_PARTS - 1
          && CALL_ARGUMENTS.equals(parts.get(1).kind())) {
        final Optional<CallTarget> target = types.callTarget(call.span());
        target.ifPresent(found -> voidArguments(parts.get(1), found, types, out));
      }
    }
  }

  private void voidArguments(
      final SyntaxNode arguments,
      final CallTarget target,
      final UnitTypeFacts types,
      final List<RuleMatch> out) {
    final List<SyntaxNode> values = plainArguments(arguments);
    for (int index = 0; index < values.size() && index < target.parameters().size(); index++) {
      final SyntaxNode value = values.get(index);
      if (returns(types, value, PROMISE_RESULT) && returnsVoid(target.parameters().get(index))) {
        out.add(new RuleMatch(value.span(), message));
      }
    }
  }

  /** Returns the expressions of the direct arguments of a call, skipping spread arguments. */
  private static List<SyntaxNode> plainArguments(final SyntaxNode arguments) {
    final List<SyntaxNode> values = new ArrayList<>();
    for (final SyntaxNode list : SyntaxText.childrenOf(arguments, ARGUMENT_LIST)) {
      for (final SyntaxNode argument : SyntaxText.childrenOf(list, ARGUMENT)) {
        if (!SyntaxText.hasToken(argument, SPREAD)) {
          SyntaxText.childrenOf(argument, EXPRESSION).forEach(values::add);
        }
      }
    }
    return values;
  }

  private static boolean isPromise(final UnitTypeFacts types, final SyntaxNode node) {
    return typeOf(types, node).filter(TypedCheck::isPromise).isPresent();
  }

  private static boolean returns(
      final UnitTypeFacts types, final SyntaxNode node, final Pattern result) {
    return typeOf(types, node).filter(type -> result.matcher(type.name()).find()).isPresent();
  }

  private static boolean returnsVoid(final TypeRef parameter) {
    return VOID_RESULT.matcher(parameter.name()).find();
  }
}
