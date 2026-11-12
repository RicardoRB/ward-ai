package dev.ward.infrastructure.init;

import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.config.WardException;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.init.RuleFileChange;
import dev.ward.domain.init.RuleFileGeneration;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.Toolchain;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import javax.inject.Inject;

/**
 * Writes ownership-marked rule files: Maven rule files for a quality preset under {@code config/},
 * and the Node tool configs in the project root.
 */
public final class PresetRuleFileWriter implements RuleFileGeneration {
  private static final String WARD_RULES = "ward-rules.toml";

  private final Map<Toolchain, RuleFileTargets> toolchains;

  /**
   * Creates the writer; {@code toolchains} holds the rule files of each toolchain that has its own,
   * and any other project, a JVM project, writes its preset rule files.
   */
  @Inject
  public PresetRuleFileWriter(final Map<Toolchain, RuleFileTargets> toolchains) {
    this.toolchains = Map.copyOf(toolchains);
  }

  @Override
  public RuleFileChange apply(
      final Project project, final QualityPreset preset, final List<InitGateOption> gates) {
    final Path config = project.root().resolve("config");
    final Map<Path, String> originals = new LinkedHashMap<>();
    final List<Path> preserved = new ArrayList<>();
    final List<String> warnings = new ArrayList<>();
    try {
      final List<RuleFileActions.Decision> decisions =
          RuleFileActions.decide(targets(project, preset, gates));
      RuleFileActions.refuseConflicts(decisions);
      // Node and Python configs live in the project root; only the Maven rule files need config/.
      final boolean createdDirectory =
          decisions.stream().anyMatch(decision -> decision.target().file().startsWith(config))
              && !Files.exists(config);
      if (createdDirectory) {
        Files.createDirectories(config);
      }
      for (final RuleFileActions.Decision decision : decisions) {
        write(decision, originals, preserved, warnings);
      }
      return new RuleFileChange(config, createdDirectory, originals, preserved, warnings);
    } catch (IOException e) {
      throw new WardException("Could not write preset rule files: " + e.getMessage(), e);
    }
  }

  @Override
  public List<PlannedFileChange> plan(
      final Project project, final QualityPreset preset, final List<InitGateOption> gates) {
    final List<PlannedFileChange> plan = new ArrayList<>();
    try {
      for (final RuleFileActions.Decision decision :
          RuleFileActions.decide(targets(project, preset, gates))) {
        final ConfigTarget target = decision.target();
        final List<String> details = target.detail() == null ? List.of() : List.of(target.detail());
        plan.add(new PlannedFileChange(target.file(), decision.action(), details));
      }
    } catch (IOException e) {
      throw new WardException("Could not read preset rule files: " + e.getMessage(), e);
    }
    return plan;
  }

  /**
   * The files the selected gates need. A Node project's targets come from its tool configs; a JVM
   * project writes each preset rule file as Ward's content.
   */
  private List<ConfigTarget> targets(
      final Project project, final QualityPreset preset, final List<InitGateOption> gates)
      throws IOException {
    final List<ConfigTarget> targets = new ArrayList<>();
    final RuleFileTargets toolchain = toolchains.get(project.toolchain());
    if (toolchain != null) {
      targets.addAll(toolchain.targets(project, preset, gates));
    } else {
      enforcerTargets(project, preset, gates)
          .forEach(
              (file, content) ->
                  targets.add(
                      new ConfigTarget(file, ConfigTarget.Disposition.WRITE, content, null)));
    }
    targets.add(
        ConfigTarget.write(
            project.root().resolve(WARD_RULES),
            ConfigFiles.marker(ConfigFiles.TOML_MARKER, preset)
                + PresetRuleFileContent.wardRules(
                    preset,
                    wardLanguages(project),
                    gates.stream().anyMatch(gate -> "build-policy".equals(gate.id())))));
    return targets;
  }

  /**
   * The language ids whose Ward rules the {@code ward} gate applies: a Node project's TypeScript
   * and JavaScript; other projects get Java or Kotlin, then Python when present, then Swift.
   */
  private static List<String> wardLanguages(final Project project) {
    final List<String> ids = new ArrayList<>();
    if (project.toolchain() == Toolchain.NODE) {
      if (project.languages().contains(Language.TYPESCRIPT)) {
        ids.add("typescript");
      }
      if (project.languages().contains(Language.JAVASCRIPT)) {
        ids.add("javascript");
      }
    } else {
      ids.addAll(jvmLanguageIds(project.languages()));
    }
    if (project.languages().contains(Language.PYTHON)) {
      ids.add("python");
    }
    ids.add("swift");
    return List.copyOf(ids);
  }

  private static List<String> jvmLanguageIds(final Set<Language> languages) {
    final List<String> ids = new ArrayList<>();
    if (languages.contains(Language.JAVA)) {
      ids.add("java");
    }
    if (languages.contains(Language.KOTLIN)) {
      ids.add("kotlin");
    }
    if (ids.isEmpty()) {
      ids.add("java");
    }
    return ids;
  }

  private static Map<Path, String> enforcerTargets(
      final Project project, final QualityPreset preset, final List<InitGateOption> gates) {
    if (preset == null) {
      return Map.of();
    }
    final Path config = project.root().resolve("config");
    final List<String> ids = gates.stream().map(InitGateOption::id).toList();
    final Map<Path, String> targets = new LinkedHashMap<>();
    if (ids.contains("enforcer")) {
      targets.put(
          config.resolve("enforcer-rules.xml"),
          marked(preset, PresetRuleFileContent.enforcer(preset)));
    }
    return targets;
  }

  /** Applies one decision, and records what a rollback or the init output needs from it. */
  private static void write(
      final RuleFileActions.Decision decision,
      final Map<Path, String> originals,
      final List<Path> preserved,
      final List<String> warnings)
      throws IOException {
    final ConfigTarget target = decision.target();
    final Path file = target.file();
    switch (decision.action()) {
      case PRESERVE_USER_OWNED -> {
        preserved.add(file);
        if (target.detail() != null) {
          warnings.add(target.detail());
        }
      }
      case CREATE, MODIFY -> {
        originals.put(file, Files.exists(file) ? Files.readString(file) : null);
        Files.writeString(file, target.content());
        if (target.disposition() == ConfigTarget.Disposition.MOVE) {
          preserved.add(file);
        }
      }
      case REMOVE -> remove(file, originals);
      default -> {
        // Already current.
      }
    }
  }

  /**
   * Removes a file, recording its content for rollback, or a directory tree that Ward owns. A tree
   * is not restored on rollback: it is a regenerable build cache.
   */
  private static void remove(final Path file, final Map<Path, String> originals)
      throws IOException {
    if (Files.isDirectory(file)) {
      try (Stream<Path> paths = Files.walk(file)) {
        for (final Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
          Files.delete(path);
        }
      }
      return;
    }
    originals.put(file, Files.readString(file));
    Files.delete(file);
  }

  private static String marked(final QualityPreset preset, final String body) {
    return RuleFileActions.MARKER + preset.id() + " -->\n" + body;
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
