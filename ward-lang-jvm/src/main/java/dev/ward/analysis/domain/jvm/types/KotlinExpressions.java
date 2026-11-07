package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.Binding;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Types the expressions of Kotlin by their shape. */
final class KotlinExpressions {
  private static final String POSTFIX = "postfixUnaryExpression";
  private static final String ATOMIC = "atomicExpression";
  private static final String SIMPLE_IDENTIFIER = "simpleIdentifier";
  private static final String EXPRESSION = "expression";
  private static final String ADDITIVE = "additiveExpression";
  private static final Set<String> BOOLEAN_OPERATIONS =
      Set.of("comparison", "equalityComparison", "conjunction", "disjunction");
  private static final Set<String> ARITHMETIC = Set.of(ADDITIVE, "multiplicativeExpression");

  private final Session session;
  private final KotlinTyper typer;
  private final KotlinCalls calls;

  KotlinExpressions(final Session session, final KotlinTyper typer) {
    this.session = session;
    this.typer = typer;
    this.calls = new KotlinCalls(session, this, typer);
  }

  Typed expr(final SyntaxNode node, final Ctx ctx) {
    final Typed typed = dispatch(node, ctx);
    session.record(node, typed);
    return typed;
  }

  private Typed dispatch(final SyntaxNode node, final Ctx ctx) {
    if (POSTFIX.equals(node.kind())) {
      return calls.postfix(node, Optional.empty(), ctx);
    }
    if (ATOMIC.equals(node.kind())) {
      return atomic(node, ctx);
    }
    final boolean wrapper =
        node.children().size() == 1 && !node.children().get(0).children().isEmpty();
    if (wrapper) {
      return expr(node.children().get(0), ctx);
    }
    final List<Typed> operands = new ArrayList<>();
    for (final SyntaxNode child : node.children()) {
      if (!child.children().isEmpty()) {
        final Typed typed = expr(child, ctx);
        if (!child.kind().toLowerCase(Locale.ROOT).contains("operat")) {
          operands.add(typed);
        }
      }
    }
    if (BOOLEAN_OPERATIONS.contains(node.kind())) {
      return Typed.of(new JType.Primitive("boolean"));
    }
    return ARITHMETIC.contains(node.kind()) ? arithmetic(node, operands) : Typed.UNKNOWN;
  }

  private static Typed arithmetic(final SyntaxNode node, final List<Typed> operands) {
    if (operands.isEmpty() || operands.stream().anyMatch(operand -> operand.type().isEmpty())) {
      return Typed.UNKNOWN;
    }
    final JType text = JType.ClassType.of(Types.STRING);
    if (ADDITIVE.equals(node.kind())
        && operands.stream().anyMatch(o -> text.equals(o.type().get()))) {
      return Typed.of(text);
    }
    JType result = operands.get(0).type().get();
    for (final Typed next : operands.stream().skip(1).toList()) {
      final JType promoted = Types.promote(result, next.type().get());
      if (promoted == null) {
        return Typed.UNKNOWN;
      }
      result = promoted;
    }
    return Typed.of(result);
  }

  private Typed atomic(final SyntaxNode node, final Ctx ctx) {
    final SyntaxNode head = node.children().get(0);
    return switch (head.kind()) {
      case "literalConstant" -> KotlinLiterals.literal(head);
      case SIMPLE_IDENTIFIER -> name(head, ctx);
      case "parenthesizedExpression" ->
          Syntax.child(head, EXPRESSION).map(inner -> expr(inner, ctx)).orElse(Typed.UNKNOWN);
      default -> {
        typer.walk(head, ctx);
        yield Typed.UNKNOWN;
      }
    };
  }

  private Typed name(final SyntaxNode identifier, final Ctx ctx) {
    final String name = Syntax.text(identifier);
    final Optional<Binding> binding = session.binding(identifier);
    if (binding.isPresent() && ScopeIndex.isValue(binding.get())) {
      return session.variableAt(binding.get()).map(Typed::of).orElse(Typed.UNKNOWN);
    }
    final Optional<Typed> inherited =
        ctx.scope().enclosing().stream()
            .map(owner -> session.hierarchy.field(session.self(owner), name))
            .flatMap(Optional::stream)
            .map(found -> Typed.of(Types.substitute(found.member().type(), found.bindings())))
            .findFirst();
    return inherited.orElseGet(
        () ->
            ctx.scope()
                .resolve(List.of(name))
                .map(binary -> Typed.naming(JType.ClassType.of(binary)))
                .orElseGet(() -> Typed.pkg(name)));
  }
}
