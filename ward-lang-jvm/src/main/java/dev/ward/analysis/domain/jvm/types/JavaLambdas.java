package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Types the lambdas of Java against the functional interfaces they implement. */
final class JavaLambdas {
  private static final String IDENTIFIER = "identifier";
  private static final String EXPRESSION = "expression";
  private static final String LAMBDA = "lambdaExpression";
  private static final String REFERENCE = "::";

  private final Session session;
  private final JavaTyper typer;
  private final JavaExpressions expressions;

  JavaLambdas(final Session session, final JavaTyper typer, final JavaExpressions expressions) {
    this.session = session;
    this.typer = typer;
    this.expressions = expressions;
  }

  static Optional<SyntaxNode> lambdaOf(final SyntaxNode expression) {
    return expression.children().size() == 1 && LAMBDA.equals(expression.children().get(0).kind())
        ? Optional.of(expression.children().get(0))
        : Optional.empty();
  }

  static boolean isReference(final SyntaxNode expression) {
    return expression.children().stream().anyMatch(child -> REFERENCE.equals(Syntax.token(child)));
  }

  /** Types a lambda or method reference argument against the parameter it is passed to. */
  void deferred(
      final SyntaxNode node,
      final Optional<JType> declared,
      final Set<String> variables,
      final Map<String, JType> bound,
      final Ctx ctx) {
    final Optional<SyntaxNode> lambda = lambdaOf(node);
    if (lambda.isEmpty()) {
      expressions.expression(node, ctx, declared);
      return;
    }
    final Optional<Inference.Shape> shape = declared.flatMap(session.inference::shape);
    final List<Optional<JType>> parameters =
        parameterTypes(lambda.get(), shape).stream()
            .map(type -> type.flatMap(found -> cleanup(found, variables)))
            .toList();
    final Optional<JType> body = body(lambda.get(), parameters, ctx);
    shape.ifPresent(
        found ->
            body.ifPresent(
                type -> session.inference.unify(found.returns(), type, variables, bound)));
    session.record(node, Typed.of(declared));
    session.record(lambda.get(), Typed.of(declared));
  }

  private List<Optional<JType>> parameterTypes(
      final SyntaxNode lambda, final Optional<Inference.Shape> shape) {
    final int count = parameters(lambda).size();
    final List<Optional<JType>> types = new ArrayList<>();
    for (int index = 0; index < count; index++) {
      final int at = index;
      types.add(
          shape
              .filter(found -> at < found.parameters().size())
              .map(found -> found.parameters().get(at)));
    }
    return types;
  }

  static Optional<JType> cleanup(final JType type, final Set<String> variables) {
    if (type instanceof JType.TypeVariable variable && variables.contains(variable.name())) {
      return Optional.empty();
    }
    if (type instanceof JType.ClassType named) {
      final List<Optional<JType>> arguments =
          named.arguments().stream().map(argument -> cleanup(argument, variables)).toList();
      return Optional.of(
          arguments.stream().allMatch(Optional::isPresent)
              ? new JType.ClassType(named.name(), arguments.stream().map(Optional::get).toList())
              : JType.ClassType.of(named.name()));
    }
    if (type instanceof JType.ArrayType array) {
      return cleanup(array.component(), variables).map(JType.ArrayType::new);
    }
    if (type instanceof JType.Wildcard wildcard && wildcard.bound() != null) {
      return cleanup(wildcard.bound(), variables)
          .map(bound -> new JType.Wildcard(bound, wildcard.upper()));
    }
    return Optional.of(type);
  }

  /** Types a lambda that is not an argument, against the type it is assigned to. */
  Typed lambda(final SyntaxNode node, final Ctx ctx, final Optional<JType> target) {
    final Optional<Inference.Shape> shape = target.flatMap(session.inference::shape);
    body(node, parameterTypes(node, shape), ctx);
    final Typed typed = Typed.of(target);
    session.record(node, typed);
    return typed;
  }

  List<SyntaxNode> parameters(final SyntaxNode lambda) {
    final List<SyntaxNode> found = new ArrayList<>();
    Syntax.child(lambda, "lambdaParameters")
        .ifPresent(
            parameters -> {
              found.addAll(Syntax.children(parameters, IDENTIFIER));
              Syntax.children(parameters, "formalParameterList")
                  .forEach(list -> found.addAll(Syntax.children(list, "formalParameter")));
              Syntax.children(parameters, "lambdaLVTIList")
                  .forEach(list -> found.addAll(Syntax.children(list, "lambdaLVTIParameter")));
            });
    return found;
  }

  private Optional<JType> body(
      final SyntaxNode lambda, final List<Optional<JType>> parameterTypes, final Ctx ctx) {
    final List<SyntaxNode> parameters = parameters(lambda);
    for (int index = 0; index < parameters.size(); index++) {
      final SyntaxNode parameter = parameters.get(index);
      final SyntaxNode name =
          IDENTIFIER.equals(parameter.kind())
              ? parameter
              : Syntax.find(parameter, IDENTIFIER).orElse(parameter);
      final Optional<JType> explicit =
          Syntax.child(parameter, "typeType")
              .flatMap(written -> JavaTypes.type(written, ctx.scope()));
      final Optional<JType> inferred = parameterTypes.get(index);
      session.variable(name, explicit.or(() -> inferred));
    }
    final Optional<SyntaxNode> body = Syntax.child(lambda, "lambdaBody");
    if (body.isEmpty()) {
      return Optional.empty();
    }
    final Optional<SyntaxNode> expression = Syntax.child(body.get(), EXPRESSION);
    if (expression.isPresent()) {
      return expressions.expression(expression.get(), ctx, Optional.empty()).type();
    }
    final List<Optional<JType>> returns = new ArrayList<>();
    typer.walk(body.get(), ctx.collecting(returns));
    Optional<JType> merged = returns.isEmpty() ? Optional.empty() : returns.get(0);
    for (final Optional<JType> next : returns.stream().skip(1).toList()) {
      merged = expressions.unify(merged, next);
    }
    return merged;
  }
}
