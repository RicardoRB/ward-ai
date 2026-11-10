package dev.ward.config;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.ClassKey;
import dagger.multibindings.IntoMap;
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
import dev.ward.analysis.frontend.antlr.format.swift.SwiftFormatSyntax;
import dev.ward.analysis.frontend.antlr.format.swift.SwiftFormatter;
import dev.ward.application.format.FormatCaches;
import dev.ward.domain.format.FormatTargets;
import dev.ward.domain.format.SourceReader;
import dev.ward.domain.format.SourceSink;
import dev.ward.infrastructure.cli.format.FormatCommand;
import dev.ward.infrastructure.format.EditorConfigFormatTargets;
import dev.ward.infrastructure.format.FileSystemSourceFiles;
import dev.ward.infrastructure.rule.FileAnalysisCache;
import java.util.Map;

/**
 * Binds the native formatters, their file selection and the file adapter of {@code ward format}.
 */
@Module
interface FormatterBindingsModule {
  /** Where the {@code ward-format} gate keeps the results of files it found formatted. */
  String CACHE_DIRECTORY = ".ward/cache/format";

  @Binds
  FormatTargets formatTargets(EditorConfigFormatTargets implementation);

  @Binds
  SourceReader sourceReader(FileSystemSourceFiles implementation);

  @Binds
  SourceSink sourceSink(FileSystemSourceFiles implementation);

  /** Registers the {@code ward format} command with the command factory. */
  @Binds
  @IntoMap
  @ClassKey(FormatCommand.class)
  Object formatCommand(FormatCommand command);

  /** Each language's formatter is paired with the parser its safety checks use. */
  @Provides
  static FormatterRegistry formatterRegistry() {
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
            new FormatterEntry(new SwiftFormatter(), new SwiftFormatSyntax())));
  }

  /** Opens the format cache of a project, under the project root. */
  @Provides
  static FormatCaches formatCaches() {
    return projectRoot -> new FileAnalysisCache(projectRoot.resolve(CACHE_DIRECTORY));
  }
}
