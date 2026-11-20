package dev.ward.analysis.domain.jvm;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Finds a chain of {@code String.replace} and {@code replaceAll} calls with literal arguments that
 * removes both carriage returns and line feeds from its receiver, as in {@code
 * value.replace("\r", "_").replace("\n", "_")} or {@code value.replaceAll("[\r\n]", "")}. Such a
 * chain gives a site named {@link #NAME} whose argument is the receiver (built by {@code
 * JavaFlowSites}), so a taint specification can list it as a sanitizer of the receiver.
 */
final class JavaLineBreakStrips {
  /** The name of the site of a chain that removes every line break of its receiver. */
  static final String NAME = "<line-breaks-removed>";

  private static final String STRING_TYPE = "java.lang.String";
  private static final String REPLACE = "replace";
  private static final String REPLACE_ALL = "replaceAll";
  private static final int ARGUMENTS = 2;
  private static final int NAME_GROUP = 1;
  private static final int TARGET_GROUP = 2;
  private static final int REPLACEMENT_GROUP = 3;
  private static final char ESCAPE = '\\';
  private static final String LITERAL = "(\"(?:[^\"\\\\]|\\\\.)*+\"|'(?:[^'\\\\]|\\\\.)++')";
  private static final Pattern TRAILING_CALL =
      Pattern.compile(
          "\\.(" + REPLACE + "|" + REPLACE_ALL + ")\\(" + LITERAL + "," + LITERAL + "\\)$"
      );
  private static final Pattern WHOLE_LITERAL = Pattern.compile("^" + LITERAL + "$");
  private static final Map<Character, Character> ESCAPES =
      Map.of(
          'n',
          '\n',
          'r',
          '\r',
          't',
          '\t',
          'b',
          '\b',
          'f',
          '\f',
          's',
          ' ',
          '"',
          '"',
          '\'',
          '\'',
          '\\',
          '\\'
      );

  /** A line break that a chain can remove. */
  private enum LineBreak {
    CARRIAGE_RETURN("\r"),
    LINE_FEED("\n");

    private final String text;

    LineBreak(final String text) {
      this.text = text;
    }
  }

  private JavaLineBreakStrips() {}

  /**
   * Returns whether the chain that ends with {@code name(arguments)} called on {@code qualifier}
   * removes both line breaks from a {@code String}.
   *
   * @param name the called name
   * @param arguments the texts of the arguments
   * @param qualifier the text of the receiver
   * @param types the type of a chain's first receiver, by its text
   */
  static boolean removesAll(
      final String name,
      final List<String> arguments,
      final String qualifier,
      final Function<String,
      Optional<String>> types
  ) {
    if (arguments.size() != ARGUMENTS || !isReplace(name)) {
      return false;
    }
    final Set<LineBreak> removed = EnumSet.noneOf(LineBreak.class);
    removed.addAll(removedBy(name, arguments.get(0), arguments.get(1)));
    String base = qualifier;
    Matcher trailing = TRAILING_CALL.matcher(base);
    while (trailing.find()) {
      removed.addAll(
          removedBy(
              trailing.group(NAME_GROUP),
              trailing.group(TARGET_GROUP),
              trailing.group(REPLACEMENT_GROUP)
          )
      );
      base = base.substring(0, trailing.start());
      trailing = TRAILING_CALL.matcher(base);
    }
    final boolean string = types.apply(base).filter(STRING_TYPE::equals).isPresent();
    return string && removed.size() == LineBreak.values().length;
  }

  private static boolean isReplace(final String name) {
    return REPLACE.equals(name) || REPLACE_ALL.equals(name);
  }

  /**
   * The line breaks that one call removes. The replacement must be a literal without a line break,
   * and, for {@code replaceAll}, without a group reference or escape, so it adds none back.
   */
  private static Set<LineBreak> removedBy(
      final String name,
      final String target,
      final String replacement
  ) {
    final Optional<String> from = value(target);
    final Optional<String> to = value(replacement);
    if (from.isEmpty() || to.isEmpty() || !safeReplacement(name, to.get())) {
      return Set.of();
    }
    return REPLACE.equals(name) ? replaced(from.get()) : matched(from.get());
  }

  private static boolean safeReplacement(final String name, final String replacement) {
    final boolean lineBreak = replacement.indexOf('\r') >= 0 || replacement.indexOf('\n') >= 0;
    final boolean special = replacement.indexOf('$') >= 0 || replacement.indexOf('\\') >= 0;
    return !lineBreak && !(REPLACE_ALL.equals(name) && special);
  }

  /** {@code replace} removes a line break when its target is that line break alone. */
  private static Set<LineBreak> replaced(final String target) {
    final Set<LineBreak> removed = EnumSet.noneOf(LineBreak.class);
    for (final LineBreak lineBreak : LineBreak.values()) {
      if (lineBreak.text.equals(target)) {
        removed.add(lineBreak);
      }
    }
    return removed;
  }

  /** {@code replaceAll} removes a line break when its pattern matches that line break alone. */
  private static Set<LineBreak> matched(final String regex) {
    final Pattern pattern;
    try {
      pattern = Pattern.compile(regex);
    } catch (final PatternSyntaxException e) {
      return Set.of();
    }
    final Set<LineBreak> removed = EnumSet.noneOf(LineBreak.class);
    for (final LineBreak lineBreak : LineBreak.values()) {
      if (pattern.matcher(lineBreak.text).matches()) {
        removed.add(lineBreak);
      }
    }
    return removed;
  }

  /**
   * The value of a string or character literal. An escape this reader does not know, such as an
   * octal or a Unicode escape, gives no value, so the chain is not taken for a sanitizer.
   */
  private static Optional<String> value(final String literal) {
    if (!WHOLE_LITERAL.matcher(literal).matches()) {
      return Optional.empty();
    }
    final String body = literal.substring(1, literal.length() - 1);
    final StringBuilder text = new StringBuilder(body.length());
    int at = 0;
    while (at < body.length()) {
      final char next = body.charAt(at);
      if (next != ESCAPE) {
        text.append(next);
        at++;
        continue;
      }
      final int code = at + 1;
      if (code >= body.length() || !ESCAPES.containsKey(body.charAt(code))) {
        return Optional.empty();
      }
      text.append(ESCAPES.get(body.charAt(code)).charValue());
      at = code + 1;
    }
    return Optional.of(text.toString());
  }
}
