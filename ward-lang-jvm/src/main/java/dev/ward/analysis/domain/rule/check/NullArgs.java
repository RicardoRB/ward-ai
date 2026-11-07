package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The nullness of the type arguments of a type or expression, read from the annotations written
 * next to them: {@code List<@Nullable String>} is one nullable argument. An argument without an
 * annotation is non-null in a {@code @NullMarked} scope, which the callers require.
 */
final class NullArgs {

  private static final String TYPE_ARGUMENT = "typeArgument";
  private static final String TYPE_TYPE = "typeType";

  private NullArgs() {}

  /** Returns the nullability of each type argument of {@code type}, or empty without arguments. */
  static Optional<List<Boolean>> of(final SyntaxNode type) {
    return SyntaxWalk.firstDescendant(type, "typeArguments", Set.of(TYPE_ARGUMENT))
        .map(NullArgs::flags);
  }

  private static List<Boolean> flags(final SyntaxNode arguments) {
    final List<Boolean> out = new ArrayList<>();
    for (final SyntaxNode argument : arguments.children()) {
      if (TYPE_ARGUMENT.equals(argument.kind())) {
        out.add(nullable(argument));
      }
    }
    return out;
  }

  private static boolean nullable(final SyntaxNode argument) {
    final List<SyntaxNode> notes = new ArrayList<>(NullSyntax.annotations(argument));
    NullSyntax.first(argument, TYPE_TYPE)
        .ifPresent(type -> notes.addAll(NullSyntax.annotations(type)));
    return NullSyntax.notes(notes).nullable();
  }

  /** The type arguments an expression has: those of {@code new T<...>()} or of a known name. */
  static Optional<List<Boolean>> ofValue(
      final SyntaxNode expression, final Map<String, List<Boolean>> env) {
    final List<SyntaxNode> parts = expression.children();
    if (parts.size() == 2 && "new".equals(SyntaxWalk.text(parts.get(0)))) {
      return NullSyntax.first(parts.get(1), "createdName").flatMap(NullArgs::of);
    }
    return Optional.ofNullable(env.get(NullSyntax.name(expression)));
  }

  /** The type arguments of the variables a method can see: fields, parameters and locals. */
  static Map<String, List<Boolean>> env(final NullModel.Type type, final NullModel.Method method) {
    final Map<String, List<Boolean>> env = new HashMap<>();
    for (final NullModel.Field field : type.fields()) {
      of(field.type()).ifPresent(flags -> env.put(field.name(), flags));
    }
    for (final NullModel.Param param : method.params()) {
      declared(param.node(), param.name(), env);
    }
    for (final SyntaxNode local :
        NullSyntax.scan(method.body(), Set.of("localVariableDeclaration"))) {
      SyntaxWalk.visits(local, Set.of("variableDeclaratorId"))
          .forEach(id -> declared(local, SyntaxWalk.text(id.node()), env));
    }
    return env;
  }

  private static void declared(
      final SyntaxNode declaration, final String name, final Map<String, List<Boolean>> env) {
    env.remove(name);
    NullSyntax.first(declaration, TYPE_TYPE)
        .flatMap(NullArgs::of)
        .ifPresent(flags -> env.put(name, flags));
  }

  /** Tells whether {@code actual} has a nullable argument where {@code declared} has none. */
  static boolean widens(final List<Boolean> declared, final List<Boolean> actual) {
    for (int index = 0; index < Math.min(declared.size(), actual.size()); index++) {
      if (actual.get(index) && !declared.get(index)) {
        return true;
      }
    }
    return false;
  }
}
