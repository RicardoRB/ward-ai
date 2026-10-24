package dev.ward.infrastructure.init;

import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.config.WardException;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.init.RuleFileChange;
import dev.ward.domain.init.RuleFileGeneration;
import dev.ward.domain.project.NodeToolConfigs;
import dev.ward.domain.project.PackageManifest;
import dev.ward.domain.project.PackageManifests;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.Toolchain;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;

/**
 * Writes ownership-marked rule files: Maven rule files for a quality preset under {@code config/},
 * and the Node tool configs in the project root.
 */
public final class PresetRuleFileWriter implements RuleFileGeneration {
  private static final String MARKER = "<!-- managed-by: ward preset=";
  private final PackageManifests manifests;
  private final NodeToolConfigs configs;

  /** Creates a writer for projects without a {@code package.json}. */
  public PresetRuleFileWriter() {
    this(root -> PackageManifest.EMPTY, (root, gate) -> Optional.empty());
  }

  /**
   * Creates the adapter; {@code manifests} tells whether a Node project uses ES modules, and {@code
   * configs} finds the tool configs it keeps itself.
   */
  @Inject
  public PresetRuleFileWriter(final PackageManifests manifests, final NodeToolConfigs configs) {
    this.manifests = manifests;
    this.configs = configs;
  }

  @Override
  public RuleFileChange apply(
      final Project project, final QualityPreset preset, final List<InitGateOption> gates) {
    final Path config = project.root().resolve("config");
    final Map<Path, String> originals = new LinkedHashMap<>();
    final List<Path> preserved = new ArrayList<>();
    try {
      final Map<Path, String> targets = targets(project, preset, gates);
      // Node configs live in the project root, so only JVM presets need config/.
      final boolean createdDirectory =
          preset != null && project.toolchain() != Toolchain.NODE && !Files.exists(config);
      if (createdDirectory) {
        Files.createDirectories(config);
      }
      for (final Map.Entry<Path, String> target : targets.entrySet()) {
        final Path file = target.getKey();
        switch (decide(file, target.getValue())) {
          case PRESERVE_USER_OWNED -> preserved.add(file);
          case CREATE, MODIFY -> {
            originals.put(file, Files.exists(file) ? Files.readString(file) : null);
            Files.writeString(file, target.getValue());
          }
          default -> {
            // Already current.
          }
        }
      }
      return new RuleFileChange(config, createdDirectory, originals, preserved);
    } catch (IOException e) {
      throw new WardException("Could not write preset rule files: " + e.getMessage(), e);
    }
  }

  @Override
  public List<PlannedFileChange> plan(
      final Project project, final QualityPreset preset, final List<InitGateOption> gates) {
    final List<PlannedFileChange> plan = new ArrayList<>();
    try {
      for (final Map.Entry<Path, String> target : targets(project, preset, gates).entrySet()) {
        plan.add(
            new PlannedFileChange(target.getKey(), decide(target.getKey(), target.getValue())));
      }
    } catch (IOException e) {
      throw new WardException("Could not read preset rule files: " + e.getMessage(), e);
    }
    return plan;
  }

  /**
   * The files the selected gates need, with their marked content; {@code null} content is a user's
   * file that is kept.
   */
  private Map<Path, String> targets(
      final Project project, final QualityPreset preset, final List<InitGateOption> gates)
      throws IOException {
    if (project.toolchain() == Toolchain.NODE) {
      return NodeConfigFiles.targets(
          project, manifests.read(project.root()), configs, preset, gates);
    }
    if (preset == null) {
      return Map.of();
    }
    final Path config = project.root().resolve("config");
    final List<String> ids = gates.stream().map(InitGateOption::id).toList();
    final Map<Path, String> targets = new LinkedHashMap<>();
    if (ids.contains("pmd")) {
      targets.put(
          config.resolve("pmd-ruleset.xml"), marked(preset, PresetRuleFileContent.pmd(preset)));
    }
    if (ids.contains("checkstyle")) {
      targets.put(
          config.resolve("checkstyle.xml"),
          marked(preset, PresetRuleFileContent.checkstyle(preset)));
    }
    if (ids.contains("spotbugs") && preset == QualityPreset.STANDARD) {
      targets.put(
          config.resolve("spotbugs-exclude.xml"),
          marked(preset, PresetRuleFileContent.spotbugs(preset)));
    }
    if (ids.contains("enforcer")) {
      targets.put(
          config.resolve("enforcer-rules.xml"),
          marked(preset, PresetRuleFileContent.enforcer(preset)));
    }
    return targets;
  }

  /** Decides what writing {@code content} to {@code file} would do; shared by apply and plan. */
  private static PlannedFileChange.Action decide(final Path file, final String content)
      throws IOException {
    if (content == null) {
      return PlannedFileChange.Action.PRESERVE_USER_OWNED;
    }
    if (!Files.exists(file)) {
      return PlannedFileChange.Action.CREATE;
    }
    final String existing = Files.readString(file);
    if (!existing.startsWith(MARKER) && !NodeConfigFiles.owned(existing)) {
      return PlannedFileChange.Action.PRESERVE_USER_OWNED;
    }
    return existing.equals(content)
        ? PlannedFileChange.Action.UNCHANGED
        : PlannedFileChange.Action.MODIFY;
  }

  private static String marked(final QualityPreset preset, final String body) {
    return MARKER + preset.id() + " -->\n" + body;
  }

  @Override
  public void rollback(final RuleFileChange change) {
    if (change == null) {
      return;
    }
    try {
      for (final Map.Entry<Path, String> entry : change.originals().entrySet()) {
        if (entry.getValue() == null) {
          Files.deleteIfExists(entry.getKey());
        } else {
          Files.writeString(entry.getKey(), entry.getValue());
        }
      }
      if (change.createdDirectory() && Files.isDirectory(change.configDirectory())) {
        Files.deleteIfExists(change.configDirectory());
      }
    } catch (IOException e) {
      throw new WardException("Could not roll back preset rule files: " + e.getMessage(), e);
    }
  }
}
