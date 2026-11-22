package dev.ward.analysis.frontend.antlr.format.rust;

import dev.ward.analysis.frontend.antlr.format.rust.RustRoles.Role;
import java.util.List;
import java.util.Set;
import org.antlr.v4.runtime.Token;

/**
 * Decides the space between two neighbouring tokens on one line: one space or none, following
 * rustfmt's style. Inside a macro's token tree the source's own spacing is kept, except around
 * commas and brackets.
 */
final class RustSpacing {
  private static final String SPACE = " ";
  private static final String NONE = "";
  private static final Set<RustKind> NO_SPACE_BEFORE =
      Set.of(
          RustKind.COMMA,
          RustKind.SEMI,
          RustKind.POSTFIX,
          RustKind.DOT,
          RustKind.ANGLE_CLOSE,
          RustKind.PIPE_CLOSE,
          RustKind.COLON,
          RustKind.BANG,
          RustKind.ANGLE_OPEN,
          RustKind.RANGE,
          RustKind.CLOSE
      );
  private static final Set<RustKind> NO_SPACE_AFTER =
      Set.of(
          RustKind.OPEN,
          RustKind.DOT,
          RustKind.PATH_SEPARATOR,
          RustKind.ANGLE_OPEN,
          RustKind.QUALIFIED_OPEN,
          RustKind.PIPE_OPEN,
          RustKind.PREFIX,
          RustKind.POUND,
          RustKind.DOLLAR,
          RustKind.RANGE
      );
  private static final Set<String> REPETITIONS = Set.of("*", "+", "?");
  private static final Set<String> CALLABLE_KEYWORDS = Set.of("pub", "fn", "unsafe");
  private static final String LINE_COMMENT = "//";

  private final List<Token> tokens;
  private final List<RustKind> kinds;
  private final RustRoles roles;

  /**
   * Prepares the rules for one source.
   *
   * @param tokens the significant tokens
   * @param kinds their kinds
   * @param roles their roles
   */
  RustSpacing(final List<Token> tokens, final List<RustKind> kinds, final RustRoles roles) {
    this.tokens = List.copyOf(tokens);
    this.kinds = List.copyOf(kinds);
    this.roles = roles;
  }

  /**
   * The text between a token and the one before it when they share a line.
   *
   * @param index the position of the later token, at least 1
   * @param between the whitespace the source has between the two
   * @return a single space or the empty string; the source's own spaces before a line comment
   */
  String gap(final int index, final String between) {
    final boolean adjacent = between.isEmpty();
    final RustKind previous = kinds.get(index - 1);
    final RustKind kind = kinds.get(index);
    if (previous == RustKind.COMMENT || kind == RustKind.COMMENT) {
      return commentGap(index, between);
    }
    if (roles.at(index) == Role.MACRO && roles.at(index - 1) == Role.MACRO) {
      return macroGap(index, previous, kind, adjacent);
    }
    return spaced(index, previous, kind) ? SPACE : NONE;
  }

  private String commentGap(final int index, final String between) {
    if (kinds.get(index) == RustKind.COMMENT && tokens.get(index).getText().startsWith(
        LINE_COMMENT
    )) {
      return between.isEmpty() ? SPACE : between;
    }
    return keep(between.isEmpty());
  }

  private String macroGap(
      final int index,
      final RustKind previous,
      final RustKind kind,
      final boolean adjacent
  ) {
    if (kind == RustKind.CLOSE || kind == RustKind.COMMA || kind == RustKind.SEMI) {
      return NONE;
    }
    if (previous == RustKind.COMMA) {
      return REPETITIONS.contains(tokens.get(index).getText()) && adjacent ? NONE : SPACE;
    }
    return previous == RustKind.OPEN ? NONE : keep(adjacent);
  }

  private static String keep(final boolean adjacent) {
    return adjacent ? NONE : SPACE;
  }

  private boolean spaced(final int index, final RustKind previous, final RustKind kind) {
    if (kind == RustKind.CLOSE) {
      return false;
    }
    if (previous == RustKind.COMMA || previous == RustKind.SEMI) {
      return true;
    }
    if (isShiftOrGlued(index, previous, kind)) {
      return false;
    }
    if (kind == RustKind.RANGE && "@".equals(tokens.get(index - 1).getText())) {
      return true;
    }
    final Boolean braces = braces(index, previous, kind);
    if (braces != null) {
      return braces;
    }
    return generalRules(index, previous, kind);
  }

  private boolean generalRules(final int index, final RustKind previous, final RustKind kind) {
    if (NO_SPACE_BEFORE.contains(kind) || NO_SPACE_AFTER.contains(previous)) {
      return false;
    }
    if (kind == RustKind.PATH_SEPARATOR) {
      return !RustKinds.endsOperand(previous);
    }
    if (kind == RustKind.OPEN) {
      return !opensLikeCall(index, previous);
    }
    return true;
  }

  private boolean opensLikeCall(final int index, final RustKind previous) {
    return RustKinds.endsOperand(previous)
        || previous == RustKind.BANG
        || previous == RustKind.KEYWORD
        && CALLABLE_KEYWORDS.contains(tokens.get(index - 1).getText())
        && "(".equals(tokens.get(index).getText());
  }

  /** The two {@code <} or the two {@code >} of a shift operator touch each other. */
  private boolean isShiftOrGlued(final int index, final RustKind previous, final RustKind kind) {
    final String text = tokens.get(index).getText();
    return previous == RustKind.BINARY
        && kind == RustKind.BINARY
        && ("<".equals(text) || ">".equals(text))
        && text.equals(tokens.get(index - 1).getText())
        && tokens.get(index - 1).getStopIndex() + 1 == tokens.get(index).getStartIndex();
  }

  private Boolean braces(final int index, final RustKind previous, final RustKind kind) {
    if (kind == RustKind.LEFT_BRACE) {
      return !hugsBrace(previous);
    }
    if (kind == RustKind.RIGHT_BRACE) {
      return previous != RustKind.LEFT_BRACE && roles.at(index) != Role.USE_BRACE;
    }
    if (previous == RustKind.LEFT_BRACE) {
      return roles.at(index - 1) != Role.USE_BRACE;
    }
    return null;
  }

  private static boolean hugsBrace(final RustKind previous) {
    return previous == RustKind.OPEN
        || previous == RustKind.PATH_SEPARATOR
        || previous == RustKind.DOLLAR
        || previous == RustKind.ANGLE_OPEN
        || previous == RustKind.POUND
        || previous == RustKind.PREFIX;
  }
}
