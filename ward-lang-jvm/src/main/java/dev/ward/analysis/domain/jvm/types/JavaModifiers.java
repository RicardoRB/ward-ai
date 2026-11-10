package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;

/** Reads the modifiers and annotations of Java declarations. */
final class JavaModifiers {
  private static final String ANNOTATION = "annotation";

  private JavaModifiers() {}

  /** Returns the access flags that the modifier nodes write. */
  static int flags(final List<SyntaxNode> modifiers) {
    int flags = 0;
    for (final SyntaxNode modifier : modifiers) {
      if (Syntax.find(modifier, ANNOTATION).isPresent()) {
        continue;
      }
      flags |=
          switch (Syntax.text(modifier)) {
            case "public" -> Flags.PUBLIC;
            case "private" -> Flags.PRIVATE;
            case "protected" -> Flags.PROTECTED;
            case "static" -> Flags.STATIC;
            case "final" -> Flags.FINAL;
            case "abstract" -> Flags.ABSTRACT;
            default -> 0;
          };
    }
    return flags;
  }

  /** Returns whether a modifier node writes {@code word}. */
  static boolean has(final List<SyntaxNode> modifiers, final String word) {
    return modifiers.stream()
        .filter(modifier -> Syntax.find(modifier, ANNOTATION).isEmpty())
        .anyMatch(modifier -> word.equals(Syntax.text(modifier)));
  }

  /** Returns the qualified names of the annotations among the modifier nodes. */
  static List<String> annotations(final List<SyntaxNode> modifiers, final TypeScope scope) {
    final List<String> names = new ArrayList<>();
    for (final SyntaxNode modifier : modifiers) {
      Syntax.find(modifier, ANNOTATION)
          .flatMap(annotation -> Syntax.child(annotation, "qualifiedName"))
          .map(name -> annotationName(Syntax.text(name), scope))
          .ifPresent(names::add);
    }
    return names;
  }

  static String annotationName(final String written, final TypeScope scope) {
    return scope.resolve(List.of(written.split("\\."))).orElse(written);
  }
}
