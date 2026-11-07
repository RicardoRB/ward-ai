package dev.ward.analysis.domain.node.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.NameReference;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Types the expressions of one unit on its scope model: literals, names, members, calls, operators
 * and function expressions. An expression that needs full checking is {@link Ty#UNKNOWN}.
 */
final class ExprTyper {
  private static final String UNDEFINED = "undefined";
  private static final String NUMBER = Literals.NUMBER;
  private static final String STRING = Literals.STRING;
  private static final String BOOLEAN = Literals.BOOLEAN;
  private static final String TYPE_ANNOTATION = "typeAnnotation";
  private static final int BINARY_PARTS = 3;
  private static final int TERNARY_PARTS = 5;

  private final UnitModel model;
  private final Program program;
  private final Members members;
  private final Map<SyntaxNode, Ty> memo = new IdentityHashMap<>();
  private final Map<SyntaxNode, Ty> contextual = new IdentityHashMap<>();
  private final CallTyper calls;
  private final FunctionTypes functions;

  ExprTyper(final UnitModel model, final Program program, final Members members) {
    this.model = model;
    this.program = program;
    this.members = members;
    this.calls = new CallTyper(this, model, members);
    this.functions = new FunctionTypes(this, model);
  }

  Members members() {
    return members;
  }

  CallTyper calls() {
    return calls;
  }

  FunctionTypes functions() {
    return functions;
  }

  /** Returns the type of an expression node. */
  Ty typeOf(final SyntaxNode node) {
    final Ty known = memo.get(node);
    if (known != null) {
      return known;
    }
    memo.put(node, Ty.UNKNOWN);
    final Ty computed = compute(node);
    memo.put(node, computed);
    return computed;
  }

  private Ty compute(final SyntaxNode node) {
    return switch (node.kind()) {
      case "expressionSequence" -> sequence(node);
      case "literal" -> Literals.of(node).orElse(Ty.UNKNOWN);
      case Exprs.IDENTIFIER_NAME, "identifier" -> identifier(node);
      case "arrayLiteral" -> arrayLiteral(node);
      case "objectLiteral" -> objectLiteral(node);
      case "anonymousFunction", "arrowFunctionDeclaration", "functionDeclaration" ->
          functions.of(node);
      case "argument" -> typeOf(Exprs.argumentValue(node));
      case Exprs.SINGLE -> single(node);
      default -> "this".equals(Exprs.token(node)) ? calls.thisType(node) : Ty.UNKNOWN;
    };
  }

  /** Records the type that the context gives to the parameter declared by {@code node}. */
  void contextualize(final SyntaxNode parameter, final Ty type) {
    contextual.putIfAbsent(parameter, type);
  }

  private Ty sequence(final SyntaxNode node) {
    final List<SyntaxNode> parts = TreeKit.children(node, Exprs.SINGLE);
    return parts.isEmpty() ? Ty.UNKNOWN : typeOf(parts.get(parts.size() - 1));
  }

  private Ty single(final SyntaxNode node) {
    final List<SyntaxNode> parts = TreeKit.parts(node);
    if (parts.isEmpty()) {
      return Ty.UNKNOWN;
    }
    if (parts.size() == 1) {
      return typeOf(parts.get(0));
    }
    final String first = Exprs.token(parts.get(0));
    return switch (first) {
      case "new" -> calls.constructed(node).map(CallTyper.Call::returns).orElse(Ty.UNKNOWN);
      case "await" -> awaited(typeOf(parts.get(1)));
      case "typeof" -> Ty.named(STRING);
      case "void" -> Ty.named(UNDEFINED);
      case "!", "delete" -> Ty.named(BOOLEAN);
      case "-", "+", "~", "++", "--" -> unary(first, parts.get(1));
      case "(" -> typeOf(parts.get(1));
      default -> infix(node, parts);
    };
  }

  private Ty unary(final String operator, final SyntaxNode operand) {
    final Ty type = typeOf(operand);
    if ("-".equals(operator)
        && type instanceof Ty.Named named
        && Character.isDigit(named.name().charAt(0))) {
      return Ty.named("-" + named.name());
    }
    return "~".equals(operator) && "bigint".equals(type.print()) ? type : Ty.named(NUMBER);
  }

  private Ty infix(final SyntaxNode node, final List<SyntaxNode> parts) {
    if (Exprs.IDENTIFIER_NAME.equals(parts.get(0).kind())) {
      return leadingCast(parts);
    }
    if (calls.isCall(parts)) {
      return calls.call(node).map(CallTyper.Call::returns).orElse(Ty.UNKNOWN);
    }
    return Exprs.memberName(parts) != null ? member(parts) : operator(parts);
  }

  private Ty operator(final List<SyntaxNode> parts) {
    final SyntaxNode left = parts.get(0);
    final String operator = Exprs.token(parts.get(1));
    return switch (operator) {
      case "[" -> element(parts);
      case "!" -> nonNull(typeOf(left));
      case "as" -> asType(parts, left);
      case "?" -> ternary(parts);
      case "=" -> typeOf(parts.get(parts.size() - 1));
      case "++", "--" -> Ty.named(NUMBER);
      default ->
          parts.size() == BINARY_PARTS
              ? Operators.binary(operator, typeOf(left), typeOf(parts.get(2)))
              : Ty.UNKNOWN;
    };
  }

  /**
   * The grammar reads {@code a as T} as the name {@code a} followed by the expression {@code as T}.
   */
  private Ty leadingCast(final List<SyntaxNode> parts) {
    final List<SyntaxNode> rest = parts.size() == 2 ? TreeKit.parts(parts.get(1)) : List.of();
    return rest.size() == 2 && "as".equals(TreeKit.text(rest.get(0)))
        ? castTo(rest.get(1))
        : Ty.UNKNOWN;
  }

  private Ty asType(final List<SyntaxNode> parts, final SyntaxNode left) {
    final SyntaxNode target = parts.get(parts.size() - 1);
    return "const".equals(TreeKit.text(target)) ? typeOf(left) : castTo(target);
  }

  private Ty castTo(final SyntaxNode target) {
    return Inst.apply(TyParser.parse(TreeKit.leaves(target)), Map.of(), model.module());
  }

  private Ty awaited(final Ty type) {
    if (type instanceof Ty.Named named
        && named.args().size() == 1
        && ("Promise".equals(named.name()) || "PromiseLike".equals(named.name()))) {
      return named.args().get(0);
    }
    return type;
  }

  private Ty member(final List<SyntaxNode> parts) {
    final String name = Exprs.memberName(parts);
    final Ty receiver = typeOf(parts.get(0));
    final boolean optional = Exprs.isOptional(parts);
    final Members.Found found = members.find(nonNullIf(receiver, optional), name);
    if (found == null) {
      return Ty.UNKNOWN;
    }
    final Ty type = found.sigs().isEmpty() ? found.type() : new Ty.Fn(found.sigs().get(0));
    return optional && type.isKnown() ? withUndefined(type) : type;
  }

  private static Ty nonNullIf(final Ty type, final boolean strip) {
    return strip ? nonNull(type) : type;
  }

  static Ty nonNull(final Ty type) {
    if (type instanceof Ty.Union union) {
      final List<Ty> kept =
          union.parts().stream().filter(part -> !Members.isNullish(part)).toList();
      return kept.size() == 1 ? kept.get(0) : new Ty.Union(kept);
    }
    return type;
  }

  private static Ty withUndefined(final Ty type) {
    return new Ty.Union(List.of(type, Ty.named(UNDEFINED)));
  }

  private Ty element(final List<SyntaxNode> parts) {
    final Ty receiver = typeOf(parts.get(0));
    return switch (receiver) {
      case Ty.Arr arr -> arr.element();
      case Ty.Tuple tuple -> tupleElement(tuple, parts);
      case Ty.Obj obj ->
          members.find(obj, "[]") == null ? Ty.UNKNOWN : members.find(obj, "[]").type();
      case Ty.Named named -> "string".equals(named.name()) ? Ty.named(STRING) : Ty.UNKNOWN;
      default -> Ty.UNKNOWN;
    };
  }

  private Ty tupleElement(final Ty.Tuple tuple, final List<SyntaxNode> parts) {
    final String index = TreeKit.text(parts.get(2));
    return index.chars().allMatch(Character::isDigit)
            && Integer.parseInt(index) < tuple.elements().size()
        ? tuple.elements().get(Integer.parseInt(index))
        : Ty.UNKNOWN;
  }

  private Ty ternary(final List<SyntaxNode> parts) {
    if (parts.size() != TERNARY_PARTS) {
      return Ty.UNKNOWN;
    }
    return Operators.join(typeOf(parts.get(2)), typeOf(parts.get(TERNARY_PARTS - 1)));
  }

  private Ty arrayLiteral(final SyntaxNode node) {
    final SyntaxNode list = TreeKit.child(node, "elementList");
    final List<Ty> types = new ArrayList<>();
    if (list == null) {
      return Ty.UNKNOWN;
    }
    for (final SyntaxNode element : TreeKit.children(list, "arrayElement")) {
      final SyntaxNode value = TreeKit.child(element, Exprs.SINGLE);
      if (TreeKit.hasToken(element, "...") || value == null) {
        return Ty.UNKNOWN;
      }
      final Ty type = Literals.widen(typeOf(value));
      if (!type.isKnown()) {
        return Ty.UNKNOWN;
      }
      if (types.stream().noneMatch(seen -> seen.print().equals(type.print()))) {
        types.add(type);
      }
    }
    return types.isEmpty()
        ? Ty.UNKNOWN
        : new Ty.Arr(types.size() == 1 ? types.get(0) : new Ty.Union(types));
  }

  private Ty objectLiteral(final SyntaxNode node) {
    final List<Member> found = new ArrayList<>();
    for (final SyntaxNode property : TreeKit.children(node, "propertyAssignment")) {
      final SyntaxNode name = TreeKit.child(property, "propertyName");
      final SyntaxNode value = TreeKit.child(property, Exprs.SINGLE);
      if (name == null || value == null || !TreeKit.hasToken(property, ":")) {
        return Ty.UNKNOWN;
      }
      final Ty type = Literals.widen(typeOf(value));
      if (!type.isKnown()) {
        return Ty.UNKNOWN;
      }
      found.add(new Member(TreeKit.unquote(TreeKit.text(name)), type, List.of(), 0, List.of()));
    }
    return new Ty.Obj(found);
  }

  private Ty identifier(final SyntaxNode node) {
    final String name = TreeKit.text(node);
    if ("true".equals(name) || "false".equals(name) || "null".equals(name)) {
      return Ty.named(name);
    }
    final NameReference reference = model.reference(node.span());
    if (reference != null && reference.resolved().isPresent()) {
      return bindingValue(reference.resolved().get(), name);
    }
    return switch (name) {
      case UNDEFINED -> Ty.named(UNDEFINED);
      case "NaN", "Infinity" -> Ty.named(NUMBER);
      case "this" -> calls.thisType(node);
      default -> Members.valueOf(program.globals().find(name), name);
    };
  }

  private Ty bindingValue(final dev.ward.analysis.domain.scope.Binding binding, final String name) {
    if (binding.kind() == BindingKind.IMPORT) {
      return Members.valueOf(model.module().find(name), name);
    }
    final UnitModel.Site site = model.site(binding.declaration());
    if (site == null) {
      return Ty.UNKNOWN;
    }
    return switch (site.kind()) {
      case VARIABLE -> variable(site);
      case PARAMETER -> parameter(site.node());
      case FUNCTION, CLASS -> Members.valueOf(model.module().find(name), name);
    };
  }

  /** Returns the type of the variable that a {@code variableDeclaration} declares. */
  Ty variable(final UnitModel.Site site) {
    final SyntaxNode annotation = TreeKit.child(site.node(), TYPE_ANNOTATION);
    if (annotation != null) {
      return Inst.apply(SigReader.annotated(annotation), Map.of(), model.module());
    }
    final List<SyntaxNode> values = TreeKit.children(site.node(), Exprs.SINGLE);
    if (!TreeKit.hasToken(site.node(), "=") || values.isEmpty()) {
      return Ty.UNKNOWN;
    }
    final Ty type = typeOf(values.get(values.size() - 1));
    return site.isConst() ? type : Literals.widen(type);
  }

  /** Returns the type of the parameter that {@code node} declares. */
  Ty parameter(final SyntaxNode node) {
    final SyntaxNode annotation = TreeKit.child(node, TYPE_ANNOTATION);
    if (annotation != null) {
      return Inst.apply(SigReader.annotated(annotation), Map.of(), model.module());
    }
    final List<SyntaxNode> values = TreeKit.children(node, Exprs.SINGLE);
    if (TreeKit.hasToken(node, "=") && !values.isEmpty()) {
      return Literals.widen(typeOf(values.get(values.size() - 1)));
    }
    return contextual.getOrDefault(node, Ty.UNKNOWN);
  }
}
