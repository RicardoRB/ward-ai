package dev.ward.analysis.domain.rule;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Turns the syntax tree of a unit into the tokens that duplicate detection compares. The leaves of
 * the tree are the tokens in source order; comments and error nodes are left out, as copy detectors
 * do. The grammar of each language names its literal, identifier and annotation kinds.
 */
public final class DuplicateTokens {

  private static final String LITERAL = "<literal>";
  private static final String IDENTIFIER = "<identifier>";
  private static final String SEPARATOR = "\u0000";
  private static final String TEXT = "text";

  /** Newline tokens are layout: copies start and end on real tokens, as copy detectors need. */
  private static final Set<String> SKIPPED = Set.of("comment", "error", "NL");

  private DuplicateTokens() {}

  /** A token: the value two tokens are compared by, and where it sits in its file. */
  public record Token(String value, Span span) {}

  /** The tokens of {@code root}, a tree of {@code language}, in source order. */
  public static List<Token> of(
      final SyntaxNode root, final AnalysisLanguage language, final DuplicateCodeOptions options) {
    final Vocabulary vocabulary = Vocabulary.of(language);
    final List<Token> tokens = new ArrayList<>();
    collect(root, vocabulary, options, tokens);
    return List.copyOf(tokens);
  }

  private static void collect(
      final SyntaxNode node,
      final Vocabulary vocabulary,
      final DuplicateCodeOptions options,
      final List<Token> tokens) {
    if (SKIPPED.contains(node.kind())
        || options.ignoreAnnotations() && vocabulary.annotations().contains(node.kind())) {
      return;
    }
    if (node.children().isEmpty()) {
      if (node.fields().containsKey(TEXT)) {
        tokens.add(new Token(value(node, vocabulary, options), node.span()));
      }
    } else {
      node.children().forEach(child -> collect(child, vocabulary, options, tokens));
    }
  }

  private static String value(
      final SyntaxNode leaf, final Vocabulary vocabulary, final DuplicateCodeOptions options) {
    if (options.ignoreLiterals() && vocabulary.literals().contains(leaf.kind())) {
      return LITERAL;
    }
    if (options.ignoreIdentifiers() && vocabulary.identifiers().contains(leaf.kind())) {
      return IDENTIFIER;
    }
    final String text = leaf.fields().get(TEXT);
    // Layout tokens such as Python's NEWLINE carry the next line's indentation as their text.
    return text.isBlank() ? leaf.kind() : leaf.kind() + SEPARATOR + text;
  }

  /** The token kinds and annotation rules of one grammar that the options act on. */
  private record Vocabulary(
      Set<String> literals, Set<String> identifiers, Set<String> annotations) {

    static Vocabulary of(final AnalysisLanguage language) {
      return switch (language) {
        case JAVA -> java();
        case KOTLIN -> kotlin();
        case TYPESCRIPT, JAVASCRIPT -> ecma();
        case PYTHON -> python();
        case SWIFT -> swift();
        case OPENAPI, ANDROID -> new Vocabulary(Set.of(), Set.of(), Set.of());
      };
    }

    private static Vocabulary java() {
      return new Vocabulary(
          Set.of(
              "DECIMAL_LITERAL",
              "HEX_LITERAL",
              "OCT_LITERAL",
              "BINARY_LITERAL",
              "FLOAT_LITERAL",
              "HEX_FLOAT_LITERAL",
              "BOOL_LITERAL",
              "CHAR_LITERAL",
              "STRING_LITERAL",
              "TEXT_BLOCK",
              "NULL_LITERAL"),
          Set.of("IDENTIFIER"),
          Set.of("annotation"));
    }

    private static Vocabulary kotlin() {
      return new Vocabulary(
          Set.of(
              "RealLiteral",
              "FloatLiteral",
              "DoubleLiteral",
              "LongLiteral",
              "IntegerLiteral",
              "HexLiteral",
              "BinLiteral",
              "BooleanLiteral",
              "NullLiteral",
              "CharacterLiteral",
              "LineStrText",
              "MultiLineStrText"),
          Set.of("Identifier"),
          Set.of("annotation", "fileAnnotation", "annotations"));
    }

    private static Vocabulary ecma() {
      return new Vocabulary(
          Set.of(
              "NullLiteral",
              "BooleanLiteral",
              "DecimalLiteral",
              "HexIntegerLiteral",
              "OctalIntegerLiteral",
              "BinaryIntegerLiteral",
              "BigHexIntegerLiteral",
              "BigOctalIntegerLiteral",
              "BigBinaryIntegerLiteral",
              "BigDecimalIntegerLiteral",
              "RegularExpressionLiteral",
              "StringLiteral"),
          Set.of("Identifier"),
          Set.of("decorator", "decoratorList"));
    }

    private static Vocabulary python() {
      return new Vocabulary(Set.of("STRING", "NUMBER"), Set.of("NAME"), Set.of("decorator"));
    }

    private static Vocabulary swift() {
      return new Vocabulary(
          Set.of(
              "Binary_literal",
              "Octal_literal",
              "Decimal_literal",
              "Hexadecimal_literal",
              "Floating_point_literal",
              "Static_string_literal",
              "Multiline_string_literal",
              "Interpolated_string_literal"),
          Set.of("Identifier"),
          Set.of("attribute"));
    }
  }
}
