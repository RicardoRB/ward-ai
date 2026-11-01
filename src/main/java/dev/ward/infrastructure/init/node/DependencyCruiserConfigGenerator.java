package dev.ward.infrastructure.init.node;

import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.config.WardException;
import dev.ward.domain.init.ArchitectureTestChange;
import dev.ward.domain.init.ArchitectureTestGeneration;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.NodeToolConfigs;
import dev.ward.domain.project.Project;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;

/**
 * Writes the dependency-cruiser config that enforces an architecture style in a Node project, or
 * keeps the project's own config. Ward's config is rewritten when the style or preset changes.
 */
public final class DependencyCruiserConfigGenerator implements ArchitectureTestGeneration {
  private static final String GATE = "dependency-cruiser";
  private static final String FILE = ".dependency-cruiser.mjs";
  private static final String TSCONFIG = "tsconfig.json";
  private static final QualityPreset.ArchitectureExtras NO_EXTRAS =
      new QualityPreset.ArchitectureExtras(false, false, false);

  private final NodeToolConfigs configs;

  /** Creates the generator that Dagger injects. */
  @Inject
  public DependencyCruiserConfigGenerator(final NodeToolConfigs configs) {
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
    final String content = content(project, architecture, extras);
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
          "Could not write dependency-cruiser config " + file + ": " + e.getMessage(), e);
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
    final boolean same = read(file).equals(content(project, architecture, extras));
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
          "Could not roll back dependency-cruiser config " + change.file() + ": " + e.getMessage(),
          e);
    }
  }

  private static String content(
      final Project project,
      final String architecture,
      final QualityPreset.ArchitectureExtras extras) {
    return DependencyCruiserConfigSource.render(
        architecture,
        extras,
        project.language() == Language.TYPESCRIPT,
        Files.exists(project.root().resolve(TSCONFIG)));
  }

  private static String read(final Path file) {
    try {
      return Files.readString(file);
    } catch (IOException e) {
      throw new WardException("Could not read " + file + ": " + e.getMessage(), e);
    }
  }
}
