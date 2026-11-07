package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.Optional;

/** Types the literals of Kotlin. */
final class KotlinLiterals {
  private static final String QUOTE = "\"";
  private static final String TRIPLE_QUOTE = "\"\"\"";
  private static final String INT = "int";

  private KotlinLiterals() {}

  static Typed literal(final SyntaxNode node) {
    final String text = Syntax.text(node);
    if (text.startsWith(QUOTE)) {
      final boolean plain = !text.contains("$") && !text.startsWith(TRIPLE_QUOTE);
      return Typed.of(JType.ClassType.of(Types.STRING))
          .withConstant(
              plain ? Optional.of(text.substring(1, text.length() - 1)) : Optional.empty());
    }
    return switch (text) {
      case "true", "false" ->
          Typed.of(new JType.Primitive("boolean")).withConstant(Optional.of(Boolean.valueOf(text)));
      case "null" -> Typed.of(JType.ClassType.of(Types.NULL));
      default -> number(text);
    };
  }

  private static Typed number(final String text) {
    if (text.startsWith("'")) {
      return Typed.of(new JType.Primitive("char"));
    }
    if (text.matches("[0-9_]+[lL]")) {
      return Typed.of(new JType.Primitive("long"));
    }
    if (text.matches("[0-9_]+")) {
      return Typed.of(new JType.Primitive(INT))
          .withConstant(Optional.of(Long.parseLong(text.replace("_", ""))));
    }
    if (text.matches("[0-9_.eE+-]+[fF]")) {
      return Typed.of(new JType.Primitive("float"));
    }
    return text.matches("[0-9_]*\\.[0-9_eE+-]+")
        ? Typed.of(new JType.Primitive("double"))
        : Typed.UNKNOWN;
  }
}
