package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.Span;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the source text around Kotlin declarations for the scope checks. The scopes keep the name
 * spans but not the modifiers, so a check reads the words written before the name on its line.
 */
final class KotlinDeclarations {

  private static final char LINE_FEED = '\n';
  private static final char AT = '@';
  private static final String FUN_KEYWORD = "(?<![\\w$])fun(?![\\w$])";

  private KotlinDeclarations() {}

  /** The text from the start of the line of {@code name} up to the name. */
  static String prefixOnLine(final String text, final Span name) {
    return text.substring(lineStart(text, name.startOffset()), name.startOffset());
  }

  /** The modifiers of a function written before its {@code fun} keyword, from its start. */
  static String modifiersBeforeFun(final String text, final int start) {
    final String header = text.substring(start, headerEnd(text, start));
    final Matcher fun = Pattern.compile(FUN_KEYWORD).matcher(header);
    return fun.find() ? header.substring(0, fun.start()) : header;
  }

  /** The text from {@code start} up to the first opening brace, the declaration's header. */
  static String header(final String text, final int start) {
    return text.substring(start, headerEnd(text, start));
  }

  /** Tells whether the word occurs in the text as a whole word. */
  static boolean hasWord(final String text, final String word) {
    return Pattern.compile("(?<![\\w$])" + Pattern.quote(word) + "(?![\\w$])").matcher(text).find();
  }

  /** Tells whether the declaration has an annotation on its line or on the line before it. */
  static boolean annotated(final String text, final Span name) {
    final String prefix = prefixOnLine(text, name);
    final int start = lineStart(text, name.startOffset());
    final boolean previousAnnotated =
        start > 0
            && text.substring(lineStart(text, start - 1), start)
                .trim()
                .startsWith(String.valueOf(AT));
    return prefix.indexOf(AT) >= 0 || previousAnnotated;
  }

  /** Tells whether the identifier occurs anywhere in the text outside the declaration itself. */
  static boolean usedElsewhere(final String text, final String name, final Span declaration) {
    final Matcher match =
        Pattern.compile("(?<![\\w$])" + Pattern.quote(name) + "(?![\\w$])").matcher(text);
    while (match.find()) {
      if (match.start() < declaration.startOffset() || match.start() >= declaration.endOffset()) {
        return true;
      }
    }
    return false;
  }

  private static int lineStart(final String text, final int offset) {
    return offset <= 0 ? 0 : text.lastIndexOf(LINE_FEED, offset - 1) + 1;
  }

  private static int headerEnd(final String text, final int start) {
    final int brace = text.indexOf('{', start);
    return brace < 0 ? text.length() : brace;
  }
}
