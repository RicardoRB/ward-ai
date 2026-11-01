package dev.ward.infrastructure.init.python;

import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.config.WardException;
import dev.ward.domain.init.ArchitectureTestChange;
import dev.ward.domain.init.ArchitectureTestGeneration;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.PythonToolConfigs;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;

/**
 * Writes the import-linter contracts that enforce an architecture style in a Python project, or
 * keeps the project's own contracts. Ward's config is rewritten when the style or preset changes.
 */
public final class ImportLinterConfigGenerator implements ArchitectureTestGeneration {
  private static final String GATE = "import-linter";
  private static final String FILE = ".importlinter";
  private static final QualityPreset.ArchitectureExtras NO_EXTRAS =
      new QualityPreset.ArchitectureExtras(false, false, false);

  private final PythonToolConfigs configs;

  /** Creates the generator that Dagger injects. */
  @Inject
  public ImportLinterConfigGenerator(final PythonToolConfigs configs) {
    this.configs = configs;
  }

  @Override
  public List<String> existingArchUnitTests(final Project project) {
    return List.of();
  }

  @Override
  public ArchitectureTestChange apply(final Project project, final String architecture) {
    return apply(project, architecture, NO_EXTRAS);
  }

  @Override
  public ArchitectureTestChange apply(
      final Project project,
      final String architecture,
      final QualityPreset.ArchitectureExtras extras) {
    final Optional<Path> userConfig = configs.userConfig(project.root(), GATE);
    if (userConfig.isPresent()) {
      return new ArchitectureTestChange(userConfig.get(), false, architecture);
    }
    final Path file = project.root().resolve(FILE);
    final String content = ImportLinterContracts.render(project, architecture, extras);
    try {
      if (!Files.exists(file)) {
        Files.writeString(file, content);
        return new ArchitectureTestChange(file, true, architecture);
      }
      final String previous = Files.readString(file);
      if (previous.equals(content)) {
        return new ArchitectureTestChange(file, false, architecture);
      }
      Files.writeString(file, content);
      return new ArchitectureTestChange(file, false, architecture, previous);
    } catch (IOException e) {
      throw new WardException(
          "Could not write import-linter config " + file + ": " + e.getMessage(), e);
    }
  }

  @Override
  public PlannedFileChange plan(
      final Project project,
      final String architecture,
      final QualityPreset.ArchitectureExtras extras) {
    final Optional<Path> userConfig = configs.userConfig(project.root(), GATE);
    if (userConfig.isPresent()) {
      return new PlannedFileChange(userConfig.get(), PlannedFileChange.Action.PRESERVE_USER_OWNED);
    }
    final Path file = project.root().resolve(FILE);
    if (!Files.exists(file)) {
      return new PlannedFileChange(file, PlannedFileChange.Action.CREATE, List.of(architecture));
    }
    final boolean same =
        read(file).equals(ImportLinterContracts.render(project, architecture, extras));
    return new PlannedFileChange(
        file,
        same ? PlannedFileChange.Action.UNCHANGED : PlannedFileChange.Action.MODIFY,
        List.of(architecture));
  }

  @Override
  public void rollback(final ArchitectureTestChange change) {
    if (change == null) {
      return;
    }
    try {
      if (change.created()) {
        Files.deleteIfExists(change.file());
      } else if (change.previous() != null) {
        Files.writeString(change.file(), change.previous());
      }
    } catch (IOException e) {
      throw new WardException(
          "Could not roll back import-linter config " + change.file() + ": " + e.getMessage(), e);
    }
  }

  private static String read(final Path file) {
    try {
      return Files.readString(file);
    } catch (IOException e) {
      throw new WardException("Could not read " + file + ": " + e.getMessage(), e);
    }
  }
}
