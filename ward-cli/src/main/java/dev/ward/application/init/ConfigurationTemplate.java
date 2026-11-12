package dev.ward.application.init;

import dev.ward.domain.gate.SupportedQualityGates;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityGatePhasePolicy;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Project;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Renders the {@code ward.toml} written by {@code ward init}. */
public final class ConfigurationTemplate {
  private static final String MINIMUM = "minimum";
  private static final String MINIMUM_BRANCHES = "minimum-branches";
  private static final int SECTION_CAPACITY = 96;
  private static final String VERSION = "version = 2\n";

  private ConfigurationTemplate() {}

  /** Renders the configuration of {@code project} with {@code gates} under {@code preset}. */
  public static String render(
      final List<InitGateOption> gates, final QualityPreset preset, final Project project) {
    return render(gates, preset, project, null);
  }

  /**
   * Renders the configuration; {@code openSpecRoot}, when not {@code null}, is written as the
   * project-relative directory the OpenSpec gates run from.
   */
  static String render(
      final List<InitGateOption> gates,
      final QualityPreset preset,
      final Project project,
      final String openSpecRoot) {
    return render(gates, preset, project, openSpecRoot, WardArchitecture.NONE);
  }

  /**
   * Renders the configuration as {@link #render(List, QualityPreset, Project, String)} does, and
   * when {@code architecture} names any table, adds the {@code ward} gate that enforces them.
   */
  static String render(
      final List<InitGateOption> gates,
      final QualityPreset preset,
      final Project project,
      final String openSpecRoot,
      final WardArchitecture architecture) {
    final StringBuilder content = new StringBuilder(SECTION_CAPACITY * (gates.size() + 1));
    content.append(VERSION);
    for (final InitGateOption gate : gates) {
      appendGate(content, gate, preset, project, openSpecRoot);
    }
    if (architecture.any()) {
      WardGateTemplate.append(content, architecture);
    }
    return content.toString();
  }

  private static void appendGate(
      final StringBuilder content,
      final InitGateOption gate,
      final QualityPreset preset,
      final Project project,
      final String openSpecRoot) {
    if (InProcessGateTemplate.handles(gate.id())) {
      InProcessGateTemplate.append(
          content, gate.id(), QualityGatePhasePolicy.profilesFor(gate.id()), preset);
      partial(content, gate, project, preset);
      return;
    }
    content
        .append("\n[checks.")
        .append(gate.id())
        .append("]\nenabled = true\nprofiles = [")
        .append(
            String.join(
                ", ",
                QualityGatePhasePolicy.profilesFor(gate.id()).stream()
                    .map(profile -> "\"" + profile + "\"")
                    .toList()))
        .append("]\n");
    if (GateCommandTemplate.writesUses(gate)) {
      content.append("uses = \"").append(gate.id()).append("\"\n");
    }
    GateCommandTemplate.append(content, gate);
    if (openSpecRoot != null && isOpenSpec(gate.id())) {
      content.append("root = \"").append(escape(openSpecRoot)).append("\"\n");
    }
    gatePreset(gate, project, preset).forEach((key, value) -> setting(content, key, value));
    partial(content, gate, project, preset);
  }

  private static boolean isOpenSpec(final String id) {
    return "openspec".equals(id) || "openspec-archived".equals(id);
  }

  private static void partial(
      final StringBuilder content,
      final InitGateOption gate,
      final Project project,
      final QualityPreset preset) {
    final String id = gate.id();
    PartialCheckDefaults.forGate(id, project, preset, gate.command())
        .or(() -> PartialCheckDefaults.forOpenApi(id, gate.settings().get("spec")))
        .ifPresent(
            table -> {
              content.append("\n[checks.").append(id).append(".paths]\n");
              array(content, "match", table.match());
              if (!table.full().isEmpty()) {
                array(content, "full", table.full());
              }
              if (!table.args().isEmpty()) {
                array(content, "args", table.args());
              }
              if (!table.command().isEmpty()) {
                array(content, "command", table.command());
              }
              string(content, "item", table.item());
              string(content, "separator", table.separator());
            });
  }

  private static void array(
      final StringBuilder content, final String key, final List<String> values) {
    content
        .append(key)
        .append(" = [")
        .append(String.join(", ", values.stream().map(ConfigurationTemplate::quoted).toList()))
        .append("]\n");
  }

  /**
   * The gate's settings, with a Gradle mutation gate's minimum taken from the preset: the Gradle
   * PIT block sets no threshold, so the summed score is checked here.
   */
  private static Map<String, String> gatePreset(
      final InitGateOption gate, final Project project, final QualityPreset preset) {
    final Integer threshold = preset == null ? null : preset.rules().mutationThreshold();
    if (!SupportedQualityGates.MUTATION.equals(gate.id())
        || project.buildTool() != BuildTool.GRADLE
        || threshold == null) {
      return gate.settings();
    }
    final Map<String, String> settings = new LinkedHashMap<>(gate.settings());
    settings.putIfAbsent(MINIMUM, String.valueOf(threshold));
    return settings;
  }

  /** Writes a gate setting: {@code minimum} is a number, every other setting a string. */
  private static void setting(final StringBuilder content, final String key, final String value) {
    if ((MINIMUM.equals(key) || MINIMUM_BRANCHES.equals(key)) && value != null) {
      content.append(key).append(" = ").append(value).append('\n');
    } else {
      string(content, key, value);
    }
  }

  private static void string(final StringBuilder content, final String key, final String value) {
    if (value != null) {
      content.append(key).append(" = ").append(quoted(value)).append('\n');
    }
  }

  private static String quoted(final String value) {
    return "\"" + escape(value) + "\"";
  }

  static String escape(final String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
