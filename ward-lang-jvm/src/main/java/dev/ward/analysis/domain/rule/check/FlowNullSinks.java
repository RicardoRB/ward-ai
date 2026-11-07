package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;

/**
 * Checks a value against the qualifier of the place it goes to: a parameter of a method of this
 * file, the return value of the method being analysed, or a field of this file.
 */
final class FlowNullSinks {

  private FlowNullSinks() {}

  /** Checks the arguments of a call against the parameters of the method of this file it runs. */
  static void call(
      final FlowNullCalls.Call call, final FlowNullState state, final FlowNullScope scope) {
    if (call.foreign()) {
      return;
    }
    scope
        .index()
        .method(call.name(), call.args().size())
        .ifPresent(
            method -> {
              for (int index = 0; index < call.args().size(); index++) {
                check(
                    call.args().get(index),
                    method.params().get(index),
                    FlowNullEvent.Kind.PARAM_NULL,
                    state,
                    scope);
              }
            });
  }

  /** Checks a returned value against the return qualifier of the method being analysed. */
  static void returned(
      final SyntaxNode value, final FlowNullState state, final FlowNullScope scope) {
    scope
        .method()
        .ifPresent(
            method -> {
              check(value, method.returns(), FlowNullEvent.Kind.RETURN_NULL, state, scope);
              if (method.optionalReturn()
                  && FlowNullValues.of(value, state, scope) == FlowNullValue.NULL) {
                scope.report(
                    FlowNullEvent.at(
                        FlowNullEvent.Kind.OPTIONAL_RETURN_NULL, value.span(), method.name()));
              }
            });
  }

  /** Checks a value stored in the field {@code name} against the qualifier of that field. */
  static void field(
      final String name,
      final SyntaxNode value,
      final FlowNullState state,
      final FlowNullScope scope) {
    scope
        .index()
        .field(name)
        .ifPresent(qual -> check(value, qual, FlowNullEvent.Kind.FIELD_NULL, state, scope));
  }

  private static void check(
      final SyntaxNode value,
      final FlowNullQual sink,
      final FlowNullEvent.Kind nullKind,
      final FlowNullState state,
      final FlowNullScope scope) {
    final FlowNullValue given = FlowNullValues.of(value, state, scope);
    final List<FlowNullEvent.Kind> kinds = kinds(given, sink, nullKind);
    kinds.forEach(
        kind -> scope.report(FlowNullEvent.at(kind, value.span(), SyntaxWalk.text(value))));
  }

  private static List<FlowNullEvent.Kind> kinds(
      final FlowNullValue given, final FlowNullQual sink, final FlowNullEvent.Kind nullKind) {
    if (sink == FlowNullQual.ALWAYS) {
      return switch (given) {
        case NULL -> List.of(nullKind);
        case NEVER -> List.of(FlowNullEvent.Kind.NEVER_TO_ALWAYS);
        case NULLABLE -> List.of(FlowNullEvent.Kind.MAYBE_TO_ALWAYS);
        default -> List.of();
      };
    }
    if (sink == FlowNullQual.NEVER) {
      return switch (given) {
        case NON_NULL, DEREFERENCED -> List.of(FlowNullEvent.Kind.ALWAYS_TO_NEVER);
        case NULLABLE -> List.of(FlowNullEvent.Kind.MAYBE_TO_NEVER);
        default -> List.of();
      };
    }
    return List.of();
  }
}
