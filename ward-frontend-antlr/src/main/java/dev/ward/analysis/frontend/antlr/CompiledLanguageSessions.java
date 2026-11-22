package dev.ward.analysis.frontend.antlr;

import dev.ward.analysis.domain.port.LanguageFrontend;
import dev.ward.analysis.frontend.antlr.csharp.CSharpLexer;
import dev.ward.analysis.frontend.antlr.csharp.CSharpParser;
import dev.ward.analysis.frontend.antlr.rust.RustLexer;
import dev.ward.analysis.frontend.antlr.rust.RustParser;
import dev.ward.analysis.frontend.antlr.swift.SwiftLexer;
import dev.ward.analysis.frontend.antlr.swift.SwiftParser;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

/** The sessions of the Swift, Rust and C# grammars, which have no option to set per unit. */
final class CompiledLanguageSessions {
  private CompiledLanguageSessions() {}

  /** A session over a Swift lexer and parser. */
  static LanguageFrontend swift() {
    final SwiftLexer lexer = new SwiftLexer(CharStreams.fromString(""));
    final SwiftParser parser = new SwiftParser(new CommonTokenStream(lexer));
    return new GrammarSession<>(lexer, parser, SwiftParser::top_level);
  }

  /** A session over a Rust lexer and parser. */
  static LanguageFrontend rust() {
    final RustLexer lexer = new RustLexer(CharStreams.fromString(""));
    final RustParser parser = new RustParser(new CommonTokenStream(lexer));
    return new GrammarSession<>(lexer, parser, RustParser::crate);
  }

  /** A session over a C# lexer and parser. */
  static LanguageFrontend csharp() {
    final CSharpLexer lexer = new CSharpLexer(CharStreams.fromString(""));
    final CSharpParser parser = new CSharpParser(new CommonTokenStream(lexer));
    return new GrammarSession<>(lexer, parser, CSharpParser::prog);
  }
}
