package dev.ward.infrastructure.init;

import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.init.ArchitectureTestChange;
import dev.ward.domain.init.ArchitectureTestGeneration;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.SwiftToolConfigs;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;

/**
 * Writes the Harmonize architecture rules of a Swift project: Ward's {@code
 * .ward/swift-architecture} package with the rules test for the style, and a root {@code
 * .harmonize.yaml} unless the project keeps its own. The project's build files are never edited.
 */
public final class HarmonizeRulesGenerator implements ArchitectureTestGeneration {
  private static final QualityPreset.ArchitectureExtras NO_EXTRAS =
      new QualityPreset.ArchitectureExtras(false, false, false);

  private final SwiftToolConfigs configs;

  /** Creates the generator that Dagger injects. */
  @Inject
  public HarmonizeRulesGenerator(final SwiftToolConfigs configs) {
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
    final Path root = project.root();
    final Path rules = root.resolve(HarmonizeRulesSource.RULES);
    final String previous = Files.exists(rules) ? HarmonizeRulesFiles.read(rules) : null;
    HarmonizeRulesFiles.write(
        root.resolve(HarmonizeRulesSource.MANIFEST), HarmonizeRulesSource.manifest());
    HarmonizeRulesFiles.write(rules, HarmonizeRulesSource.rules(architecture));
    if (configs.userConfig(root, HarmonizeRulesSource.ROOT_CONFIG).isEmpty()) {
      HarmonizeRulesFiles.write(
          root.resolve(HarmonizeRulesSource.ROOT_CONFIG), HarmonizeRulesSource.rootConfig());
    }
    return new ArchitectureTestChange(rules, previous == null, architecture, previous);
  }

  @Override
  public PlannedFileChange plan(
      final Project project,
      final String architecture,
      final QualityPreset.ArchitectureExtras extras) {
    final Path root = project.root();
    final Path rules = root.resolve(HarmonizeRulesSource.RULES);
    final List<String> notes = new ArrayList<>(List.of(architecture + " architecture"));
    if (configs.userConfig(root, HarmonizeRulesSource.ROOT_CONFIG).isPresent()) {
      notes.add("kept your .harmonize.yaml: add .ward to its excludes");
    }
    if (!Files.exists(rules)) {
      return new PlannedFileChange(rules, PlannedFileChange.Action.CREATE, notes);
    }
    final boolean same =
        HarmonizeRulesFiles.read(rules).equals(HarmonizeRulesSource.rules(architecture));
    return new PlannedFileChange(
        rules, same ? PlannedFileChange.Action.UNCHANGED : PlannedFileChange.Action.MODIFY, notes);
  }

  /**
   * Undoes a change: a package the change created is removed with the root config Ward wrote, and a
   * rewritten rules file gets its previous content back.
   */
  @Override
  public void rollback(final ArchitectureTestChange change) {
    if (change == null) {
      return;
    }
    if (change.created()) {
      final Path packageDirectory = change.file().resolve("../../..").normalize();
      HarmonizeRulesFiles.removeTree(packageDirectory);
      HarmonizeRulesFiles.removeWardRootConfig(packageDirectory.resolve("../..").normalize());
    } else if (change.previous() != null) {
      HarmonizeRulesFiles.write(change.file(), change.previous());
    }
  }

  /** Whether {@code file} is a rules file this generator wrote, by its Swift file name. */
  static boolean owns(final Path file) {
    return file.toString().endsWith(".swift");
  }
}
