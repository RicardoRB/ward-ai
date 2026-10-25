package dev.ward.application.doctor;

import dev.ward.application.doctor.DoctorService.Finding;
import dev.ward.application.doctor.DoctorService.Status;
import dev.ward.domain.doctor.EnvironmentFacts;
import dev.ward.domain.init.BuildToolConfiguration;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.JavaSupport;
import dev.ward.domain.project.Project;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Checks the Maven or Gradle wrapper, or the system build tool when no wrapper exists, and the JDK
 * that runs the build together with the gate versions pinned for it.
 */
final class JvmToolchainFinding implements ToolchainFinding {
  private static final String JDK = "jdk";

  @Override
  public List<Finding> findings(
      final Project detected, final EnvironmentFacts facts, final ToolchainContext context) {
    final String toolName = detected.buildTool().displayName();
    final String wrapperName =
        detected.buildTool() == BuildTool.GRADLE ? "Gradle Wrapper" : "Maven Wrapper";
    final String executable = detected.buildTool() == BuildTool.GRADLE ? "gradle" : "mvn";
    final String wrapperFlag =
        detected.buildTool() == BuildTool.GRADLE
            ? "gradleWrapperAvailable"
            : "mavenWrapperAvailable";
    final boolean wrapper = Boolean.parseBoolean(facts.values().get(wrapperFlag));
    final String toolMessage =
        wrapper ? wrapperName + " available." : "System " + toolName + " is required.";
    final List<Finding> findings = new ArrayList<>();
    findings.add(
        new Finding(
            toolName.toLowerCase(Locale.ROOT),
            wrapper || facts.hasExecutable(executable) ? Status.OK : Status.ERROR,
            toolMessage));
    context.buildTools().ifPresent(tools -> findings.addAll(jdkFindings(detected, tools, context)));
    return findings;
  }

  /** The runtime JDK, the supported-range warning, and each pinned gate version it cannot run. */
  private static List<Finding> jdkFindings(
      final Project detected, final BuildToolConfiguration tools, final ToolchainContext context) {
    final Optional<Integer> jdk = tools.runtimeJdk(detected);
    if (jdk.isEmpty()) {
      return List.of(
          new Finding(
              JDK,
              Status.WARNING,
              "The runtime JDK could not be determined: neither the build tool nor its wrapper"
                  + " reported a Java version."));
    }
    final List<Finding> findings = new ArrayList<>();
    findings.add(
        JavaSupport.warning(jdk.get())
            .map(message -> new Finding(JDK, Status.WARNING, message))
            .orElseGet(() -> new Finding(JDK, Status.OK, "JDK " + jdk.get() + " runs the build.")));
    tools.unsupportedPins(detected, context.configuredGates()).stream()
        .map(pin -> new Finding(pin.gate(), Status.WARNING, pin.reason() + "."))
        .forEach(findings::add);
    return findings;
  }
}
