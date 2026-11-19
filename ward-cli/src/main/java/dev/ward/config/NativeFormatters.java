package dev.ward.config;

import dev.ward.analysis.domain.format.FormatterEntry;
import dev.ward.analysis.domain.format.FormatterRegistry;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.frontend.antlr.format.ecma.JavaScriptFormatSyntax;
import dev.ward.analysis.frontend.antlr.format.ecma.JavaScriptFormatter;
import dev.ward.analysis.frontend.antlr.format.ecma.TypeScriptFormatSyntax;
import dev.ward.analysis.frontend.antlr.format.ecma.TypeScriptFormatter;
import dev.ward.analysis.frontend.antlr.format.java.JavaFormatSyntax;
import dev.ward.analysis.frontend.antlr.format.java.JavaFormatter;
import dev.ward.analysis.frontend.antlr.format.kotlin.KotlinFormatSyntax;
import dev.ward.analysis.frontend.antlr.format.kotlin.KotlinFormatter;
import dev.ward.analysis.frontend.antlr.format.python.PythonFormatSyntax;
import dev.ward.analysis.frontend.antlr.format.python.PythonFormatter;
import dev.ward.analysis.frontend.antlr.format.rust.RustFormatSyntax;
import dev.ward.analysis.frontend.antlr.format.rust.RustFormatter;
import dev.ward.analysis.frontend.antlr.format.swift.SwiftFormatSyntax;
import dev.ward.analysis.frontend.antlr.format.swift.SwiftFormatter;
import java.util.Map;

/** The native formatter of every language that has one, with the syntax that checks its output. */
final class NativeFormatters {
  private NativeFormatters() {}

  /**
   * Builds the registry of the native formatters.
   *
   * @return one entry per formatted language
   */
  static FormatterRegistry registry() {
    return new FormatterRegistry(
        Map.of(
            AnalysisLanguage.JAVA,
            new FormatterEntry(new JavaFormatter(), new JavaFormatSyntax()),
            AnalysisLanguage.KOTLIN,
            new FormatterEntry(new KotlinFormatter(), new KotlinFormatSyntax()),
            AnalysisLanguage.TYPESCRIPT,
            new FormatterEntry(new TypeScriptFormatter(), new TypeScriptFormatSyntax()),
            AnalysisLanguage.JAVASCRIPT,
            new FormatterEntry(new JavaScriptFormatter(), new JavaScriptFormatSyntax()),
            AnalysisLanguage.PYTHON,
            new FormatterEntry(new PythonFormatter(), new PythonFormatSyntax()),
            AnalysisLanguage.SWIFT,
            new FormatterEntry(new SwiftFormatter(), new SwiftFormatSyntax()),
            AnalysisLanguage.RUST,
            new FormatterEntry(new RustFormatter(), new RustFormatSyntax())
        )
    );
  }
}
