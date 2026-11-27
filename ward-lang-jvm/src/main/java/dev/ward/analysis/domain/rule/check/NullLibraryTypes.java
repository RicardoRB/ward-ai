package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.Optional;
import java.util.Set;

/** Finds the declared type of a name in a method: a local, a parameter, a catch parameter or a field. */
final class NullLibraryTypes {
  private static final String TYPE_TYPE = "typeType";
  private static final String DECLARATOR = "variableDeclaratorId";

  private NullLibraryTypes() {}

  /** Returns the simple name, without type arguments, of the declared type of {@code name}. */
  static Optional<String> declared(
      final String name,
      final NullModel.Type type,
      final NullModel.Method method
  ) {
    final Optional<String> local = local(name, method);
    if (local.isPresent()) {
      return local;
    }
    final Optional<String> param =
        method.params().stream()
            .filter(candidate -> candidate.name().equals(name))
            .findFirst()
            .flatMap(candidate -> NullSyntax.first(candidate.node(), TYPE_TYPE))
            .map(NullLibraryTypes::simple);
    if (param.isPresent()) {
      return param;
    }
    return type.fields().stream()
        .filter(field -> field.name().equals(name) || ("this." + field.name()).equals(name))
        .findFirst()
        .map(field -> simple(field.type()));
  }

  private static Optional<String> local(final String name, final NullModel.Method method) {
    for (final SyntaxNode declaration :
        NullSyntax.scan(method.body(), Set.of("localVariableDeclaration", "catchClause"))) {
      final boolean named =
          SyntaxWalk.visits(declaration, Set.of(DECLARATOR)).stream()
              .anyMatch(visit -> SyntaxWalk.text(visit.node()).equals(name));
      final Optional<SyntaxNode> written = typeOf(declaration);
      if (named && written.isPresent()) {
        return written.map(NullLibraryTypes::simple);
      }
    }
    return Optional.empty();
  }

  private static Optional<SyntaxNode> typeOf(final SyntaxNode declaration) {
    return SyntaxWalk.firstDescendant(declaration, TYPE_TYPE, Set.of())
        .or(() -> SyntaxWalk.firstDescendant(declaration, "catchType", Set.of()));
  }

  private static String simple(final SyntaxNode type) {
    final String text = SyntaxWalk.text(type);
    final int generic = text.indexOf('<');
    final String bare = generic < 0 ? text : text.substring(0, generic);
    return bare.substring(bare.lastIndexOf('.') + 1);
  }
}
