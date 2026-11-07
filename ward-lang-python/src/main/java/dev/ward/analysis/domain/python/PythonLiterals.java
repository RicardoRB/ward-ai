package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.types.TypeRef;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** The types of literal tokens and of list, set and dict displays. */
final class PythonLiterals {

  private static final String TRUE = "True";
  private static final String FALSE = "False";
  private static final String NONE = "None";
  private static final String BOOL = "bool";
  private static final String STR = "str";
  private static final String BYTES = "bytes";
  private static final String INT = "int";
  private static final String FLOAT = "float";
  private static final String COMPLEX = "complex";
  private static final String LIST = "list";
  private static final String DICT = "dict";
  private static final String SET = "set";
  private static final String HEX = "0x";
  private static final String OCTAL = "0o";
  private static final String BINARY = "0b";
  private static final String LIST_OPEN = "[";
  private static final String SET_OPEN = "{";
  private static final String COLON = ":";
  private static final String KEYWORD_MARKERS = "**";
  private static final int PAIR_PARTS = 2;

  private PythonLiterals() {}

  /** The type of a literal token, or empty when {@code token} is not a literal. */
  static Optional<TypeRef> of(final SyntaxNode token) {
    final String text = PythonSyntax.text(token);
    if (PythonTypeKinds.NUMBER.equals(token.kind())) {
      return Optional.of(TypeRef.of(numberType(text)));
    }
    if (PythonTypeKinds.STRING.equals(token.kind())) {
      return Optional.of(TypeRef.of(stringType(text)));
    }
    if (TRUE.equals(text) || FALSE.equals(text)) {
      return Optional.of(TypeRef.of(BOOL));
    }
    return NONE.equals(text) ? Optional.of(TypeRef.of(NONE)) : Optional.empty();
  }

  /** The built-in class of a list, set or dict display whose parts are {@code parts}. */
  static Optional<String> collectionName(final List<SyntaxNode> parts) {
    final String open = PythonSyntax.text(parts.get(0));
    if (LIST_OPEN.equals(open)) {
      return Optional.of(LIST);
    }
    if (SET_OPEN.equals(open)) {
      return Optional.of(isMapping(parts) ? DICT : SET);
    }
    return Optional.empty();
  }

  private static boolean isMapping(final List<SyntaxNode> parts) {
    if (parts.size() == PAIR_PARTS) {
      return true;
    }
    final SyntaxNode contents = parts.get(1);
    return PythonTypeKinds.DICTORSETMAKER.equals(contents.kind())
        && contents.children().stream()
            .map(PythonSyntax::text)
            .anyMatch(text -> COLON.equals(text) || KEYWORD_MARKERS.equals(text));
  }

  private static String numberType(final String text) {
    final String lower = text.toLowerCase(Locale.ROOT);
    if (lower.endsWith("j")) {
      return COMPLEX;
    }
    if (lower.startsWith(HEX) || lower.startsWith(OCTAL) || lower.startsWith(BINARY)) {
      return INT;
    }
    return lower.contains(".") || lower.contains("e") ? FLOAT : INT;
  }

  private static String stringType(final String text) {
    final int quote = Math.max(text.indexOf('\''), text.indexOf('"'));
    final String prefix = quote < 0 ? "" : text.substring(0, quote).toLowerCase(Locale.ROOT);
    return prefix.contains("b") ? BYTES : STR;
  }
}
