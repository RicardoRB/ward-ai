package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;

/** The rules that follow a null value out of a method: returns, assignments, calls and uses. */
final class NullValueFinders {

  private static final String EXPRESSION = "expression";
  private static final String LOCAL = "localVariableDeclaration";

  private NullValueFinders() {}

  /** Runs {@code visit} for each method of each type that has a body. */
  static List<NullHit> methods(final NullFinder.Unit unit, final MethodVisitor visit) {
    final List<NullHit> hits = new ArrayList<>();
    for (final NullModel.Type type : unit.types()) {
      for (final NullModel.Method method : type.methods()) {
        if (method.body() != null) {
          visit.visit(type, method, hits);
        }
      }
    }
    return hits;
  }

  /** Reads one method into {@code hits}. */
  @FunctionalInterface
  interface MethodVisitor {
    void visit(NullModel.Type type, NullModel.Method method, List<NullHit> hits);
  }

  /** A null, or a nullable name, returned from a method that must not return null. */
  static List<NullHit> returnNullable(final NullFinder.Unit unit) {
    return methods(
        unit,
        (type, method, hits) -> {
          if (method.returns() != NullModel.Returns.REFERENCE
              || !NullFlow.nonNull(method.notes(), type.marked(), false)) {
            return;
          }
          final Set<String> names = NullFlow.nullable(type, method);
          final Set<String> calls = NullFlow.nullableCalls(type);
          for (final SyntaxNode value : NullFlow.returned(method.body())) {
            if (NullFlow.nullish(value, names, calls)) {
              hits.add(NullHit.at(value, SyntaxWalk.text(value)));
            }
          }
        });
  }

  /** A null, or a nullable name, assigned to a primitive local or returned as a primitive. */
  static List<NullHit> unboxNullable(final NullFinder.Unit unit) {
    return methods(
        unit,
        (type, method, hits) -> {
          final Set<String> names = NullFlow.nullable(type, method);
          final Set<String> calls = NullFlow.nullableCalls(type);
          if (method.returns() == NullModel.Returns.PRIMITIVE) {
            NullFlow.returned(method.body()).stream()
                .filter(value -> NullFlow.nullable(value, names, calls))
                .forEach(value -> hits.add(NullHit.at(value, SyntaxWalk.text(value))));
          }
          for (final SyntaxNode local : NullSyntax.scan(method.body(), Set.of(LOCAL))) {
            final Optional<SyntaxNode> type2 = NullSyntax.first(local, "typeType");
            if (type2.isPresent() && NullSyntax.isPrimitive(type2.get())) {
              initializers(local).stream()
                  .filter(value -> NullFlow.nullable(value, names, calls))
                  .forEach(value -> hits.add(NullHit.at(value, SyntaxWalk.text(value))));
            }
          }
        });
  }

  private static List<SyntaxNode> initializers(final SyntaxNode local) {
    return SyntaxWalk.visits(local, Set.of("variableInitializer")).stream()
        .flatMap(visit -> visit.node().children().stream())
        .filter(node -> EXPRESSION.equals(node.kind()))
        .toList();
  }

  /** A null, or a nullable name, assigned to a field that must not be null. */
  static List<NullHit> assignField(final NullFinder.Unit unit) {
    return methods(
        unit,
        (type, method, hits) -> {
          final Set<String> names = NullFlow.nullable(type, method);
          final Set<String> calls = NullFlow.nullableCalls(type);
          final Set<String> shadowed = shadowed(method);
          for (final SyntaxNode expression : NullSyntax.expressions(method.body())) {
            if (!"=".equals(NullSyntax.operator(expression))) {
              continue;
            }
            final SyntaxNode value = expression.children().get(2);
            if (nonNullField(type, expression.children().get(0), shadowed)
                && NullFlow.nullish(value, names, calls)) {
              hits.add(NullHit.at(value, SyntaxWalk.text(value)));
            }
          }
        });
  }

  private static boolean nonNullField(
      final NullModel.Type type, final SyntaxNode target, final Set<String> shadowed) {
    final String text = SyntaxWalk.text(target);
    final String name = NullSyntax.name(target);
    final boolean plain = text.equals(name);
    return type.fields().stream()
        .anyMatch(
            field ->
                field.name().equals(name)
                    && !(plain && shadowed.contains(name))
                    && NullFlow.nonNull(field.notes(), type.marked(), field.primitive()));
  }

  /** The parameter and local names of a method, which hide the fields of the same name. */
  static Set<String> shadowed(final NullModel.Method method) {
    final Set<String> names = new HashSet<>();
    method.params().forEach(param -> names.add(param.name()));
    for (final SyntaxNode local : NullSyntax.scan(method.body(), Set.of(LOCAL))) {
      SyntaxWalk.visits(local, Set.of("variableDeclaratorId"))
          .forEach(visit -> names.add(SyntaxWalk.text(visit.node())));
    }
    return names;
  }

  /** A switch expression with a null result, returned from a method that must not return null. */
  static List<NullHit> switchExpressionNullable(final NullFinder.Unit unit) {
    return methods(
        unit,
        (type, method, hits) -> {
          if (method.returns() != NullModel.Returns.REFERENCE
              || !NullFlow.nonNull(method.notes(), type.marked(), false)) {
            return;
          }
          for (final SyntaxNode value : NullFlow.returned(method.body())) {
            NullSyntax.first(value, "switchExpression")
                .ifPresent(
                    expression ->
                        SyntaxWalk.visits(expression, Set.of("switchRuleOutcome")).stream()
                            .map(SyntaxWalk.Visit::node)
                            .filter(outcome -> "null;".equals(SyntaxWalk.text(outcome)))
                            .forEach(outcome -> hits.add(NullHit.at(outcome, "null"))));
          }
        });
  }

  /** Runs {@code action} for each hit finder; kept for the registry in {@link NullnessCheck}. */
  static void each(final BiConsumer<String, NullFinder> action) {
    action.accept("return-nullable", NullValueFinders::returnNullable);
    action.accept("unbox-nullable", NullValueFinders::unboxNullable);
    action.accept("assign-field-nullable", NullValueFinders::assignField);
    action.accept("switch-expression-nullable", NullValueFinders::switchExpressionNullable);
  }
}
