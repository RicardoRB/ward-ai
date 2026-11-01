package dev.ward.application.init;

import dev.ward.domain.config.CommandTypeInference;
import dev.ward.domain.gate.SupportedQualityGates;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityGatePhasePolicy;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Project;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Renders the {@code ward.toml} written by {@code ward init}. */
final class ConfigurationTemplate {
  private static final String MINIMUM = "minimum";
  private static final int SECTION_CAPACITY = 96;
  private static final String VERSION = "version = 2\n";

  /** Types written with {@code uses} even when their command would infer them. */
  private static final Set<String> CRITICAL_USES =
      Set.of("archunit", "openspec", "openspec-archived");

  private ConfigurationTemplate() {}

  static String render(
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
    final StringBuilder content = new StringBuilder(SECTION_CAPACITY * (gates.size() + 1));
    content.append(VERSION);
    for (final InitGateOption gate : gates) {
      final List<String> command = gate.command();
      final String goal =
          command.subList(1, command.size()).stream()
              .map(ConfigurationTemplate::escape)
              .reduce((left, right) -> left + " " + right)
              .orElse("");
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
      if (writesUses(gate)) {
        content.append("uses = \"").append(gate.id()).append("\"\n");
      }
      if (!SupportedQualityGates.TRACEABILITY.equals(gate.id())) {
        content
            .append("command = \"")
            .append(escape(command.getFirst()))
            .append(command.size() > 1 ? " " : "")
            .append(goal)
            .append("\"\n");
      }
      if (openSpecRoot != null && isOpenSpec(gate.id())) {
        content.append("root = \"").append(escape(openSpecRoot)).append("\"\n");
      }
      gatePreset(gate, project, preset).forEach((key, value) -> setting(content, key, value));
      partial(content, gate, project, preset);
    }
    return content.toString();
  }

  /**
   * Whether the check needs {@code uses}: its command does not imply its type, or the type is one
   * that must never depend on command shape.
   */
  private static boolean writesUses(final InitGateOption gate) {
    return CRITICAL_USES.contains(gate.id())
        || !CommandTypeInference.infer(gate.command()).equals(gate.id());
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
    if (MINIMUM.equals(key) && value != null) {
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

  private static String escape(final String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
