package dev.ward.analysis.frontend.antlr.csharp;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.atn.LexerATNSimulator;

/**
 * Evaluates the conditional-compilation directives of C# while the lexer runs: {@code #define},
 * {@code #undef}, {@code #if}, {@code #elif}, {@code #else} and {@code #endif}. The text of a false
 * branch becomes one token on the skipped channel, so the parser never sees it. No symbol is
 * defined at the start of a file; the project's own symbols are not read. Derived from the
 * preprocessing of the {@code CSharpLexerBase} of the grammars-v4 {@code csharp/v8-spec} grammar
 * (MIT).
 */
final class CSharpPreprocessor {
  private final CSharpLexerBase lexer;
  private final Queue<Token> pending = new ArrayDeque<>();
  private final Set<String> symbols = new HashSet<>();
  private final Deque<Boolean> condition = new ArrayDeque<>();
  private final Deque<Boolean> taken = new ArrayDeque<>();

  CSharpPreprocessor(final CSharpLexerBase lexer) {
    this.lexer = lexer;
  }

  /** Forgets the state of the previous unit. */
  void reset() {
    pending.clear();
    symbols.clear();
    condition.clear();
    taken.clear();
  }

  /** Returns the next token, handling the directive tokens the lexer put on their channel. */
  Token next() {
    if (!pending.isEmpty()) {
      return pending.poll();
    }
    final Token token = lexer.rawNextToken();
    if (token.getChannel() == CSharpLexer.DIRECTIVE) {
      final Token skipped = handle(token.getType());
      if (skipped != null) {
        pending.add(skipped);
      }
    }
    return token;
  }

  private Token handle(final int type) {
    return switch (type) {
      case CSharpLexer.DEFINE -> define(true);
      case CSharpLexer.UNDEF -> define(false);
      case CSharpLexer.KW_IF -> branch(false, true);
      case CSharpLexer.ELIF -> branch(true, true);
      case CSharpLexer.KW_ELSE -> branch(true, false);
      case CSharpLexer.ENDIF -> endIf();
      default -> null;
    };
  }

  private Token define(final boolean add) {
    final String symbol = DirectiveExpression.symbolOf(collectLine());
    if (isActive() && symbol != null) {
      if (add) {
        symbols.add(symbol);
      } else {
        symbols.remove(symbol);
      }
    }
    return null;
  }

  /** Handles {@code #if}, {@code #elif} (with a condition) and {@code #else}. */
  private Token branch(final boolean continuing, final boolean conditional) {
    final List<Token> line = collectLine();
    final boolean alreadyTaken = continuing && popTaken();
    if (continuing) {
      popCondition();
    }
    final boolean wanted = !alreadyTaken && isActive() && (!conditional || new DirectiveExpression(
        line,
        symbols
    ).evaluate());
    condition.push(wanted);
    taken.push(alreadyTaken || wanted || !conditional);
    return wanted ? null : skipBranch();
  }

  /** Reads the false branch and moves the lexer's line and column past the text it consumed. */
  private Token skipBranch() {
    final Token skipped = FalseBranchScanner.scan(
        lexer.getInputStream(),
        lexer.getLine(),
        lexer.getCharPositionInLine()
    );
    final String text = skipped.getText();
    final LexerATNSimulator position = lexer.getInterpreter();
    final int lastBreak = text.lastIndexOf('\n');
    position.setLine(position.getLine() + (int)text.chars().filter(c -> c == '\n').count());
    position.setCharPositionInLine(
        lastBreak < 0 ? position.getCharPositionInLine() + text.length()
            : text.length() - lastBreak - 1
    );
    return skipped;
  }

  private boolean popTaken() {
    return !taken.isEmpty() && taken.pop();
  }

  private void popCondition() {
    if (!condition.isEmpty()) {
      condition.pop();
    }
  }

  private Token endIf() {
    collectLine();
    popCondition();
    popTaken();
    return null;
  }

  private boolean isActive() {
    return condition.isEmpty() || condition.peek();
  }

  private List<Token> collectLine() {
    final List<Token> tokens = new ArrayList<>();
    Token token;
    do {
      token = lexer.rawNextToken();
      if (token.getChannel() != Lexer.HIDDEN) {
        tokens.add(token);
      }
    } while (token.getType() != CSharpLexer.DIRECTIVE_NEW_LINE && token.getType() != Token.EOF);
    return tokens;
  }
}
