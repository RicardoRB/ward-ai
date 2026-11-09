package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;

/**
 * Records the classes that the names of a cast target and of a catch clause write, so that the
 * checks of unsafe casts and unreachable catch blocks can read their type facts.
 */
final class KotlinNamedTypes {
  private static final String SIMPLE_IDENTIFIER = "simpleIdentifier";
  private static final String SIMPLE_USER_TYPE = "simpleUserType";
  private static final String USER_TYPE = "userType";
  private static final String PLAIN_NAME = "\\w+";

  private KotlinNamedTypes() {}

  /**
   * Records the class that a cast target names when the target is a plain name, as in {@code x as
   * Name}. A qualified or nullable target is not recorded, so its type stays unknown.
   */
  static void recordCastTarget(
      final Session session, final SyntaxNode target, final TypeScope scope) {
    final String text = Syntax.text(target);
    if (text.matches(PLAIN_NAME)) {
      KotlinTypes.named(List.of(text), List.of(), false, scope)
          .ifPresent(type -> session.record(target, Typed.of(type)));
    }
  }

  /** Records the class that the exception type of a catch clause names. */
  static void recordCatchType(
      final Session session, final SyntaxNode catchBlock, final TypeScope scope) {
    Syntax.child(catchBlock, USER_TYPE)
        .ifPresent(
            userType -> {
              final List<String> names =
                  Syntax.children(userType, SIMPLE_USER_TYPE).stream()
                      .map(
                          simple ->
                              Syntax.text(Syntax.child(simple, SIMPLE_IDENTIFIER).orElse(simple)))
                      .toList();
              KotlinTypes.named(names, List.of(), false, scope)
                  .ifPresent(type -> session.record(userType, Typed.of(type)));
            });
  }
}
