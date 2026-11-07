package dev.ward.analysis.domain.node.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Types the expressions of one unit on its scope model: literals, names, members, calls, operators
 * and function expressions. An expression that needs full checking is {@link TsType#UNKNOWN}.
 */
final class ExprTyper {
  private static final String STRING = Literals.STRING;
  private static final String UNDEFINED = "undefined";

  private final Members members;
  private final Map<SyntaxNode, TsType> memo = new IdentityHashMap<>();
  private final CallTyper calls;
  private final ConstructTyper constructs;
  private final FunctionTypes functions;
  private final LiteralTypes literals;
  private final MemberTypes memberTypes;
  private final OperatorTypes operators;
  private final NameTypes names;

  ExprTyper(final UnitModel model, final Program program, final Members members) {
    this.members = members;
    final ArgumentTyper arguments = new ArgumentTyper(this, members);
    this.calls = new CallTyper(this, model, members, arguments);
    this.constructs = new ConstructTyper(this, model, arguments);
    this.functions = new FunctionTypes(this, model);
    this.literals = new LiteralTypes(this::typeOf);
    this.memberTypes = new MemberTypes(this::typeOf, members);
    this.operators = new OperatorTypes(this::typeOf, model, memberTypes);
    this.names = new NameTypes(this::typeOf, model, program, constructs);
  }

  Members members() {
    return members;
  }

  CallTyper calls() {
    return calls;
  }

  ConstructTyper constructs() {
    return constructs;
  }

  FunctionTypes functions() {
    return functions;
  }

  NameTypes names() {
    return names;
  }

  /** Returns the type of an expression node. */
  TsType typeOf(final SyntaxNode node) {
    final TsType known = memo.get(node);
    if (known != null) {
      return known;
    }
    memo.put(node, TsType.UNKNOWN);
    final TsType computed = compute(node);
    memo.put(node, computed);
    return computed;
  }

  private TsType compute(final SyntaxNode node) {
    return switch (node.kind()) {
      case "expressionSequence" -> operators.sequence(node);
      case "literal" -> Literals.of(node).orElse(TsType.UNKNOWN);
      case Exprs.IDENTIFIER_NAME, "identifier" -> names.identifier(node);
      case "arrayLiteral" -> literals.arrayLiteral(node);
      case "objectLiteral" -> literals.objectLiteral(node);
      case "anonymousFunction", "arrowFunctionDeclaration", "functionDeclaration" ->
          functions.of(node);
      case "argument" -> typeOf(Exprs.argumentValue(node));
      case Exprs.SINGLE -> single(node);
      default -> "this".equals(Exprs.token(node)) ? constructs.thisType(node) : TsType.UNKNOWN;
    };
  }

  private TsType single(final SyntaxNode node) {
    final List<SyntaxNode> parts = TreeKit.parts(node);
    if (parts.isEmpty()) {
      return TsType.UNKNOWN;
    }
    if (parts.size() == 1) {
      return typeOf(parts.get(0));
    }
    final String first = Exprs.token(parts.get(0));
    return switch (first) {
      case "new" ->
          constructs.constructed(node).map(CallTyper.Call::returns).orElse(TsType.UNKNOWN);
      case "await" -> OperatorTypes.awaited(typeOf(parts.get(1)));
      case "typeof" -> TsType.named(STRING);
      case "void" -> TsType.named(UNDEFINED);
      case "!", "delete" -> TsType.named(Literals.BOOLEAN);
      case "-", "+", "~", "++", "--" -> operators.unary(first, parts.get(1));
      case "(" -> typeOf(parts.get(1));
      default -> infix(node, parts);
    };
  }

  private TsType infix(final SyntaxNode node, final List<SyntaxNode> parts) {
    if (Exprs.IDENTIFIER_NAME.equals(parts.get(0).kind())) {
      return operators.leadingCast(parts);
    }
    if (calls.isCall(parts)) {
      return calls.call(node).map(CallTyper.Call::returns).orElse(TsType.UNKNOWN);
    }
    return Exprs.memberName(parts) != null ? memberTypes.member(parts) : operators.operator(parts);
  }
}
