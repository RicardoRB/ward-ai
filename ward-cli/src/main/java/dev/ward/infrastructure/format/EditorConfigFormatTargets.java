package dev.ward.infrastructure.format;

import dev.ward.analysis.domain.format.FormatOption;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.domain.format.FormatConfiguration;
import dev.ward.domain.format.FormatTarget;
import dev.ward.domain.format.FormatTargets;
import dev.ward.domain.process.CommandExecutor;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;

/** Selects the project's files and resolves their settings from the {@code .editorconfig} files. */
public final class EditorConfigFormatTargets implements FormatTargets {

  private final CommandExecutor executor;

  /**
   * Creates the selection. The executor asks Git which files are ignored.
   *
   * @param executor the command executor
   */
  @Inject
  public EditorConfigFormatTargets(final CommandExecutor executor) {
    this.executor = executor;
  }

  @Override
  public List<FormatTarget> select(
      final Path root, final Map<AnalysisLanguage, List<FormatOption>> declared) {
    final FormatConfiguration configuration = new EditorConfigFormatConfiguration(root);
    return new FormatFileSelection(configuration, executor).select(root, declared);
  }
}
