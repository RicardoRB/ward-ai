package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.api.ApiTypeRef;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;

/** Reads a Kotlin type that does not resolve, such as a function type, by its written text. */
final class KotlinWrittenTypes {
  private static final String NULLABLE = "?";
  private static final String NAME_SYMBOLS = ".`_";

  private KotlinWrittenTypes() {}

  /** Returns the text of {@code written} with its head name qualified when it resolves. */
  static ApiTypeRef of(final SyntaxNode written, final TypeScope scope) {
    final String all = stripNullable(Syntax.text(written));
    final int cut = headEnd(all);
    final String head = all.substring(0, cut);
    final String qualified =
        scope.resolve(List.of(head.split("\\."))).map(ApiTypeRefs::source).orElse(head);
    final String full = qualified + all.substring(cut);
    return new ApiTypeRef(full, full, qualified, false);
  }

  private static int headEnd(final String text) {
    int cut = 0;
    while (cut < text.length() && isNameCharacter(text.charAt(cut))) {
      cut++;
    }
    return cut;
  }

  private static boolean isNameCharacter(final char character) {
    return Character.isLetterOrDigit(character) || NAME_SYMBOLS.indexOf(character) >= 0;
  }

  private static String stripNullable(final String text) {
    String found = text;
    while (found.endsWith(NULLABLE)) {
      found = found.substring(0, found.length() - 1);
    }
    return found;
  }
}
