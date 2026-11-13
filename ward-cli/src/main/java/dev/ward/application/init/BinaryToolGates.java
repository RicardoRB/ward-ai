package dev.ward.application.init;

import dev.ward.domain.config.OpenApiGateOptions;
import dev.ward.domain.doctor.EnvironmentInspection;
import dev.ward.domain.gate.SupportedQualityGates;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.project.Project;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Gate choices that run a standalone executable, available only when it is on the path. */
record BinaryToolGates(EnvironmentInspection environment, Project project) {
  private static final String SPEC = "spec";

  /** The executable-backed gates, in wizard order. */
  List<InitGateOption> gates() {
    final List<InitGateOption> gates = new ArrayList<>(securityGates());
    environment.openApiSpec(project.root()).ifPresent(spec -> gates.addAll(openApiGates(spec)));
    return List.copyOf(gates);
  }

  /** The contract gates, offered only for a Git-tracked OpenAPI spec they can check. */
  private List<InitGateOption> openApiGates(final String spec) {
    return List.of(
        withSettings(
            new InitGateOption(
                SupportedQualityGates.OPENAPI_LINT,
                "Lints the committed OpenAPI spec with Ward's own rules; nothing to install.",
                List.of(),
                true,
                "Ward's own rules; no executable is needed."),
            Map.of(SPEC, spec)),
        withSettings(
            nativeOption(
                SupportedQualityGates.OPENAPI_DIFF,
                "Reports breaking changes in uncommitted OpenAPI spec edits."),
            Map.of(SPEC, spec)),
        withSettings(
            nativeOption(
                SupportedQualityGates.OPENAPI_COMPAT,
                "Reports breaking OpenAPI changes on the branch against origin/main."),
            orderedSettings(spec)));
  }

  /** A gate Ward runs itself: always available, with no command. */
  private static InitGateOption nativeOption(final String id, final String description) {
    return new InitGateOption(
        id, description, List.of(), true, "Runs inside Ward; nothing to install.");
  }

  private static Map<String, String> orderedSettings(final String spec) {
    final Map<String, String> settings = new LinkedHashMap<>();
    settings.put(SPEC, spec);
    settings.put("base", OpenApiGateOptions.DEFAULT_BASE);
    return settings;
  }

  private static InitGateOption withSettings(
      final InitGateOption option, final Map<String, String> settings) {
    return new InitGateOption(
        option.id(),
        option.description(),
        option.command(),
        option.available(),
        option.availabilityMessage(),
        settings);
  }

  /**
   * The security gates: native secret detection and ZAP. Ward's own security rules run in the
   * {@code ward} gate, so no external source scanner is offered.
   */
  private List<InitGateOption> securityGates() {
    return List.of(
        new InitGateOption(
            SupportedQualityGates.SECRETS,
            "Detects committed secrets with Ward's native secret rules (no install needed).",
            List.of(),
            true,
            "Runs inside Ward; nothing to install."),
        option(
            "zap",
            "Runs an OWASP ZAP baseline DAST scan against a running application; replace <TARGET_URL> in -t with an authorized target before running.",
            List.of("zap-baseline.py", "-t", SupportedQualityGates.ZAP_TARGET_PLACEHOLDER),
            "Install ZAP and replace "
                + SupportedQualityGates.ZAP_TARGET_PLACEHOLDER
                + " with a target you are authorized to scan before running."));
  }

  private InitGateOption option(
      final String id, final String description, final List<String> command, final String hint) {
    final boolean available = environment.inspect(project.root()).hasExecutable(command.getFirst());
    final String message =
        available
            ? "Executable available: " + command.getFirst()
            : command.getFirst() + " is not on the path. " + hint;
    return new InitGateOption(id, description, command, available, message);
  }
}
