package dev.ward.infrastructure.rule;

import dev.ward.application.gate.AnalysisRuleLoad;
import dev.ward.application.gate.AnalysisRuleSource;
import dev.ward.application.gate.RuleConfigFile;
import java.nio.file.Path;
import java.util.Optional;
import javax.inject.Inject;

/**
 * The rules packaged with Ward, and the {@code ward-rules.toml} of the project that selects and
 * tunes them.
 */
public final class ProjectRuleSource implements AnalysisRuleSource {
  private final ClasspathRuleSource packaged;

  /** Creates the source over the packaged rule files. */
  @Inject
  public ProjectRuleSource(final ClasspathRuleSource packaged) {
    this.packaged = packaged;
  }

  @Override
  public AnalysisRuleLoad load() {
    return packaged.load();
  }

  @Override
  public Optional<RuleConfigFile> configuration(final Path root, final String file) {
    return RuleConfigFileReader.read(root, file);
  }
}
