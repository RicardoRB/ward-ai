package dev.ward.analysis.frontend.antlr;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.LanguageFrontend;
import dev.ward.analysis.domain.port.ParseResult;
import dev.ward.analysis.frontend.antlr.java.JavaLexer;
import dev.ward.analysis.frontend.antlr.java.JavaParser;
import dev.ward.analysis.frontend.antlr.javascript.JavaScriptLexer;
import dev.ward.analysis.frontend.antlr.javascript.JavaScriptParser;
import dev.ward.analysis.frontend.antlr.kotlin.KotlinLexer;
import dev.ward.analysis.frontend.antlr.kotlin.KotlinParser;
import dev.ward.analysis.frontend.antlr.python.Python3Lexer;
import dev.ward.analysis.frontend.antlr.python.Python3Parser;
import dev.ward.analysis.frontend.antlr.swift.SwiftLexer;
import dev.ward.analysis.frontend.antlr.swift.SwiftParser;
import dev.ward.analysis.frontend.antlr.typescript.TypeScriptLexer;
import dev.ward.analysis.frontend.antlr.typescript.TypeScriptParser;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;

/**
 * The ANTLR frontend for every {@link AnalysisLanguage}. Each language keeps one lexer and one
 * parser, reset per unit, so parsing is serialized; the results are cached, so any number of
 * threads and passes that ask for the same unit share one parse. Create one instance per run and
 * hand it to every pass.
 */
public final class AntlrLanguageFrontend implements LanguageFrontend {

  /**
   * The weight of the parses kept, in characters of source text, so the passes of a run parse a
   * unit once. It is about 8 MB of source: Java trees weigh about 90 bytes per character, so the
   * cache holds well under a gigabyte of trees however large the project is.
   */
  private static final long RECENT_WEIGHT = 8L * 1024 * 1024;

  private final Map<AnalysisLanguage, LanguageFrontend> sessions =
      new EnumMap<>(AnalysisLanguage.class);
  private final ParseCache recent = new ParseCache(RECENT_WEIGHT);

  /** Creates the lexers and parsers for all six languages. */
  public AntlrLanguageFrontend() {
    for (final AnalysisLanguage language : AnalysisLanguage.values()) {
      sessions.put(language, session(language));
    }
  }

  @Override
  public ParseResult parse(final SourceUnit unit) {
    return recent.parse(unit, key -> sessions.get(key.language()).parse(key));
  }

  private static LanguageFrontend session(final AnalysisLanguage language) {
    return switch (language) {
      case JAVA -> javaSession();
      case KOTLIN -> kotlinSession();
      case TYPESCRIPT -> typeScriptSession();
      case JAVASCRIPT -> javaScriptSession();
      case PYTHON -> pythonSession();
      case SWIFT -> swiftSession();
    };
  }

  private static LanguageFrontend javaSession() {
    return new JavaSession(
        javaUnitSession(JavaParser::compilationunit),
        javaUnitSession(JavaParser::compactCompilationUnit));
  }

  private static LanguageFrontend javaUnitSession(
      final Function<JavaParser, ParserRuleContext> start) {
    final JavaLexer lexer = new JavaLexer(empty());
    final JavaParser parser = new JavaParser(new CommonTokenStream(lexer));
    return new GrammarSession<>(lexer, parser, start);
  }

  private static LanguageFrontend kotlinSession() {
    final KotlinLexer lexer = new KotlinLexer(empty());
    final KotlinParser parser = new KotlinParser(new CommonTokenStream(lexer));
    return new GrammarSession<>(lexer, parser, KotlinParser::kotlinFile);
  }

  /** TypeScript allows {@code <T>value} casts, so JSX is recognized in {@code .tsx} units only. */
  private static LanguageFrontend typeScriptSession() {
    final TypeScriptLexer lexer = new TypeScriptLexer(empty());
    final TypeScriptParser parser = new TypeScriptParser(new CommonTokenStream(lexer));
    final LanguageFrontend session = new GrammarSession<>(lexer, parser, TypeScriptParser::program);
    return unit -> {
      lexer.setJsx(unit.path().endsWith(".tsx"));
      return session.parse(unit);
    };
  }

  /** JavaScript has no type assertion that JSX could clash with, so JSX is always recognized. */
  private static LanguageFrontend javaScriptSession() {
    final JavaScriptLexer lexer = new JavaScriptLexer(empty());
    final JavaScriptParser parser = new JavaScriptParser(new CommonTokenStream(lexer));
    lexer.setJsx(true);
    return new GrammarSession<>(lexer, parser, JavaScriptParser::program);
  }

  private static LanguageFrontend pythonSession() {
    final Python3Lexer lexer = new Python3Lexer(empty());
    final Python3Parser parser = new Python3Parser(new CommonTokenStream(lexer));
    return new GrammarSession<>(lexer, parser, Python3Parser::file_input);
  }

  private static LanguageFrontend swiftSession() {
    final SwiftLexer lexer = new SwiftLexer(empty());
    final SwiftParser parser = new SwiftParser(new CommonTokenStream(lexer));
    return new GrammarSession<>(lexer, parser, SwiftParser::top_level);
  }

  private static CharStream empty() {
    return CharStreams.fromString("");
  }
}
