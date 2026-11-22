package dev.ward.config;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.ClassKey;
import dagger.multibindings.IntoMap;
import dev.ward.analysis.domain.format.FormatterRegistry;
import dev.ward.application.format.FormatCaches;
import dev.ward.domain.format.FormatTargets;
import dev.ward.domain.format.SourceReader;
import dev.ward.domain.format.SourceSink;
import dev.ward.infrastructure.cli.format.FormatCommand;
import dev.ward.infrastructure.format.EditorConfigFormatTargets;
import dev.ward.infrastructure.format.FileSystemSourceFiles;
import dev.ward.infrastructure.rule.FileAnalysisCache;

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
    return NativeFormatters.registry();
  }

  /** Opens the format cache of a project, under the project root. */
  @Provides
  static FormatCaches formatCaches() {
    return projectRoot -> new FileAnalysisCache(projectRoot.resolve(CACHE_DIRECTORY));
  }
}
