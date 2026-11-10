package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

/** The rules about {@code @RequiresNonNull} and {@code @EnsuresNonNull} contracts of one file. */
final class NullContracts {

  private static final String REQUIRES = "RequiresNonNull";
  private static final String ENSURES = "EnsuresNonNull";
  private static final String THIS = "this.";

  private NullContracts() {}

  /** Returns the field names that the annotations named {@code name} list. */
  static Set<String> values(final NullModel.Notes notes, final String name) {
    final Set<String> out = new LinkedHashSet<>();
    notes.nodes().stream()
        .filter(node -> name.equals(NullSyntax.simpleName(node)))
        .forEach(node -> out.addAll(strings(node)));
    return out;
  }

  private static List<String> strings(final SyntaxNode annotation) {
    final List<String> out = new ArrayList<>();
    for (final SyntaxNode leaf : SyntaxWalk.leaves(annotation)) {
      final String text = leaf.fields().get("text");
      if (text.length() > 1 && text.startsWith("\"") && text.endsWith("\"")) {
        final String value = text.substring(1, text.length() - 1);
        out.add(value.startsWith(THIS) ? value.substring(THIS.length()) : value);
      }
    }
    return out;
  }

  private static Set<String> fieldNames(final NullModel.Type type, final boolean nullableOnly) {
    final Set<String> names = new LinkedHashSet<>();
    type.fields().stream()
        .filter(field -> !nullableOnly || field.notes().nullable())
        .forEach(field -> names.add(field.name()));
    return names;
  }

  /** A contract that names something that is not a field of the type. */
  static List<NullHit> annotationValueInvalid(final NullFinder.Unit unit) {
    final List<NullHit> hits = new ArrayList<>();
    for (final NullModel.Type type : unit.types()) {
      if (!type.supertypes().isEmpty()) {
        continue;
      }
      final Set<String> fields = fieldNames(type, false);
      for (final NullModel.Method method : type.methods()) {
        for (final SyntaxNode note : method.notes().nodes()) {
          final String name = NullSyntax.simpleName(note);
          if (REQUIRES.equals(name) || ENSURES.equals(name)) {
            strings(note).stream()
                .filter(value -> value.indexOf('.') < 0 && !fields.contains(value))
                .forEach(value -> hits.add(NullHit.at(note, value)));
          }
        }
      }
    }
    return hits;
  }

  /** A method that ensures a field is non-null and never sets it. */
  static List<NullHit> postconditionNotSatisfied(final NullFinder.Unit unit) {
    final List<NullHit> hits = new ArrayList<>();
    for (final NullModel.Type type : unit.types()) {
      for (final NullModel.Method method : type.methods()) {
        if (method.body() == null) {
          continue;
        }
        final Set<String> shadowed = NullValueFinders.shadowed(method);
        values(method.notes(), ENSURES).stream()
            .filter(field -> field.indexOf('.') < 0)
            .filter(field -> !NullFlow.assigns(method.body(), field, shadowed))
            .forEach(field -> hits.add(NullHit.at(method.node(), field)));
      }
    }
    return hits;
  }

  /** A call of a method that requires a nullable field to be non-null, with no check of it. */
  static List<NullHit> preconditionNotSatisfied(final NullFinder.Unit unit) {
    final List<NullHit> hits = new ArrayList<>();
    for (final NullModel.Type type : unit.types()) {
      for (final NullModel.Method caller : type.methods()) {
        if (caller.body() != null) {
          checkCalls(type, caller, hits);
        }
      }
    }
    return hits;
  }

  private static void checkCalls(
      final NullModel.Type type, final NullModel.Method caller, final List<NullHit> hits) {
    final Set<String> guarded = NullFlow.guarded(caller);
    final Set<String> nullable = fieldNames(type, true);
    final Set<String> held = values(caller.notes(), REQUIRES);
    for (final SyntaxNode expression : NullSyntax.expressions(caller.body())) {
      NullFlow.call(expression)
          .filter(call -> call.receiver().isEmpty() || "this".equals(call.receiver()))
          .ifPresent(
              call ->
                  type.methods().stream()
                      .filter(callee -> callee.name().equals(call.name()))
                      .filter(callee -> callee.params().size() == call.args().size())
                      .flatMap(callee -> values(callee.notes(), REQUIRES).stream())
                      .filter(field -> nullable.contains(field) && !guarded.contains(field))
                      .filter(field -> !held.contains(field))
                      .forEach(field -> hits.add(NullHit.at(expression, field))));
    }
  }

  /** Runs {@code action} for each rule of this class. */
  static void each(final BiConsumer<String, NullFinder> action) {
    action.accept("annotation-value-invalid", NullContracts::annotationValueInvalid);
    action.accept("postcondition-not-satisfied", NullContracts::postconditionNotSatisfied);
    action.accept("precondition-not-satisfied", NullContracts::preconditionNotSatisfied);
  }
}
