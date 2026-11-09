package dev.ward.analysis.domain.node.lint;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The promise values that flow into a void return: a function declared to return void, a variable
 * declared as returning void, and a call argument whose parameter returns void.
 */
final class VoidPromises {

  private static final String DECLARATION = "variableDeclaration";
  private static final String FUNCTION_DECLARATION = "functionDeclaration";
  private static final String ARROW_FUNCTION = "arrowFunctionDeclaration";
  private static final String EXPRESSION = "singleExpression";
  private static final String CALL_ARGUMENTS = "arguments";
  private static final String ARGUMENT_LIST = "argumentList";
  private static final String ARGUMENT = "argument";
  private static final String SPREAD = "...";
  private static final Pattern PROMISE_RESULT = Pattern.compile("=>\\s*Promise\\b");
  private static final Pattern VOID_RESULT = Pattern.compile("=>\\s*void$");

  private VoidPromises() {}

  /** Returns every promise that flows into a void return of the unit. */
  static List<RuleMatch> find(
      final SyntaxNode root, final UnitTypeFacts types, final String message) {
    final List<RuleMatch> out = new ArrayList<>();
    functions(root, types, message, out);
    variables(root, types, message, out);
    arguments(root, types, message, out);
    return out;
  }

  /** Reports a promise returned from a function whose declared return type is void. */
  private static void functions(
      final SyntaxNode root,
      final UnitTypeFacts types,
      final String message,
      final List<RuleMatch> out) {
    for (final SyntaxNode function : SyntaxText.nodesOf(root, FUNCTION_DECLARATION)) {
      returnsOf(function, types, message, out);
    }
    for (final SyntaxNode function : SyntaxText.nodesOf(root, ARROW_FUNCTION)) {
      returnsOf(function, types, message, out);
    }
  }

  private static void returnsOf(
      final SyntaxNode function,
      final UnitTypeFacts types,
      final String message,
      final List<RuleMatch> out) {
    for (final SyntaxNode value : VoidFunctions.returnedValues(function)) {
      if (promiseValue(types, value)
          || typeOf(types, value).filter(VoidPromises::isPromiseFunction).isPresent()) {
        out.add(new RuleMatch(value.span(), message));
      }
    }
  }

  /** Reports a promise-returning value stored in a variable whose declared type returns void. */
  private static void variables(
      final SyntaxNode root,
      final UnitTypeFacts types,
      final String message,
      final List<RuleMatch> out) {
    for (final SyntaxNode declaration : SyntaxText.nodesOf(root, DECLARATION)) {
      final SyntaxNode value = SyntaxText.last(declaration);
      final boolean typedAsVoid =
          typeOf(types, declaration).filter(VoidPromises::returnsVoid).isPresent();
      if (value != null
          && EXPRESSION.equals(value.kind())
          && typedAsVoid
          && typeOf(types, value).filter(VoidPromises::isPromiseFunction).isPresent()) {
        out.add(new RuleMatch(value.span(), message));
      }
    }
  }

  /** Reports a promise-returning function argument whose parameter is typed as returning void. */
  private static void arguments(
      final SyntaxNode root,
      final UnitTypeFacts types,
      final String message,
      final List<RuleMatch> out) {
    for (final SyntaxNode call : SyntaxText.nodesOf(root, EXPRESSION)) {
      final List<SyntaxNode> parts = call.children();
      if (parts.size() == 2 && CALL_ARGUMENTS.equals(parts.get(1).kind())) {
        final Optional<CallTarget> target = types.callTarget(call.span());
        target.ifPresent(found -> voidArguments(parts.get(1), found, types, message, out));
      }
    }
  }

  private static void voidArguments(
      final SyntaxNode arguments,
      final CallTarget target,
      final UnitTypeFacts types,
      final String message,
      final List<RuleMatch> out) {
    final List<SyntaxNode> values = plainArguments(arguments);
    for (int index = 0; index < values.size() && index < target.parameters().size(); index++) {
      final SyntaxNode value = values.get(index);
      if (typeOf(types, value).filter(VoidPromises::isPromiseFunction).isPresent()
          && returnsVoid(target.parameters().get(index))) {
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

  private static boolean promiseValue(final UnitTypeFacts types, final SyntaxNode node) {
    return typeOf(types, node).filter(TypedCheck::isPromise).isPresent();
  }

  private static Optional<TypeRef> typeOf(final UnitTypeFacts types, final SyntaxNode node) {
    return TypedCheck.typeOf(types, node);
  }

  private static boolean isPromiseFunction(final TypeRef type) {
    return PROMISE_RESULT.matcher(type.name()).find();
  }

  private static boolean returnsVoid(final TypeRef type) {
    return VOID_RESULT.matcher(type.name()).find();
  }
}
