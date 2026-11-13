package dev.ward;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchRule;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

final class ArchitectureBoundarySupport {
  // dev.ward.domain.<feature>: the feature is the fourth package segment.
  private static final int FEATURE_SEGMENT = 3;

  /** The config package's modules, which wire the Dagger graph and may be placed there. */
  static final Set<String> CONFIG_MODULES =
      Set.of(
          "dev.ward.config.WardComponent",
          "dev.ward.config.NativeTypeFactsBindings",
          "dev.ward.config.PortBindingsModule",
          "dev.ward.config.ProcessBindingsModule",
          "dev.ward.config.ProjectBindingsModule",
          "dev.ward.config.InitBindingsModule",
          "dev.ward.config.BuildToolBindingsModule",
          "dev.ward.config.DoctorBindingsModule",
          "dev.ward.config.DependencyBindingsModule",
          "dev.ward.config.BuildPolicyBindingsModule",
          "dev.ward.config.VulnerabilityBindingsModule",
          "dev.ward.config.LicenseBindingsModule",
          "dev.ward.config.NodeBindingsModule",
          "dev.ward.config.PythonBindingsModule",
          "dev.ward.config.SwiftBindingsModule",
          "dev.ward.config.CommandBindingsModule",
          "dev.ward.config.GateBindingsModule",
          "dev.ward.config.ApiCompatBindingsModule",
          "dev.ward.config.GateExtensionBindingsModule",
          "dev.ward.config.RuleBindingsModule",
          "dev.ward.config.GateExtensionsModule",
          "dev.ward.config.FormatterBindingsModule",
          "dev.ward.config.ReportBindingsModule",
          "dev.ward.config.RulesBindingsModule",
          "dev.ward.config.RulesCommandBindingsModule",
          "dev.ward.config.WardProvidesModule");

  /** The analysis packages that every layer may hold. */
  static final Set<String> ANALYSIS_PACKAGES =
      Set.of(
          "dev.ward.analysis.domain.api",
          "dev.ward.analysis.domain.graph",
          "dev.ward.analysis.domain.ir",
          "dev.ward.analysis.domain.port",
          "dev.ward.analysis.domain.rule",
          "dev.ward.analysis.domain.rule.check",
          "dev.ward.analysis.domain.rule.metric",
          "dev.ward.analysis.domain.flow",
          "dev.ward.analysis.domain.flow.taint",
          "dev.ward.analysis.domain.format",
          "dev.ward.analysis.domain.scope",
          "dev.ward.analysis.domain.types",
          "dev.ward.analysis.application.rule");

  /**
   * The ANTLR frontend is an outbound adapter in its own module, {@code ward-frontend-antlr}.
   * ward-cli depends on it to run the {@code ward} gate, so its classes, including the generated
   * parsers, are on this classpath. Its placement is checked by its own module, not by the CLI
   * layers.
   */
  static final Map<String, Set<String>> DOMAIN_FEATURE_EDGES =
      Map.ofEntries(
          Map.entry("agent", Set.of()),
          Map.entry("apicompat", Set.of()),
          Map.entry("buildpolicy", Set.of("dependency", "vulnerability")),
          Map.entry("config", Set.of()),
          Map.entry("dependency", Set.of("config", "project", "process")),
          Map.entry("doctor", Set.of()),
          Map.entry("gate", Set.of("project", "process")),
          Map.entry("format", Set.of("config")),
          Map.entry("init", Set.of("agent", "gate", "project")),
          Map.entry("loop", Set.of("gate")),
          Map.entry("policy", Set.of("gate")),
          Map.entry("process", Set.of()),
          Map.entry("terminal", Set.of()),
          Map.entry("project", Set.of()),
          Map.entry("json", Set.of()),
          Map.entry("license", Set.of("config", "dependency", "project")),
          Map.entry("learning", Set.of("gate")),
          Map.entry("secrets", Set.of()),
          Map.entry("update", Set.of()),
          Map.entry("vulnerability", Set.of("dependency", "gate")),
          Map.entry("rules", Set.of()));

  static final Map<String, Set<String>> APPLICATION_ENTRY_POINTS =
      Map.ofEntries(
          Map.entry("loop", Set.of("dev.ward.application.gate.CheckService")),
          Map.entry("format", Set.of()),
          Map.entry(
              "buildpolicy", Set.of("dev.ward.application.dependency.DependencyInventoryService")),
          Map.entry(
              "gate",
              Set.of(
                  "dev.ward.application.buildpolicy.BuildPolicyService",
                  "dev.ward.application.buildpolicy.BuildPolicySetting",
                  "dev.ward.application.project.ProjectDetector",
                  "dev.ward.application.project.ProjectDiscovery",
                  "dev.ward.application.project.ProjectNotFoundException",
                  "dev.ward.application.format.FormatService",
                  "dev.ward.application.format.FormatFileOutcome",
                  "dev.ward.application.format.FormatFileOutcome$Verdict",
                  "dev.ward.application.vulnerability.EcosystemStatus",
                  "dev.ward.application.vulnerability.ScanOptions",
                  "dev.ward.application.vulnerability.ScanResult",
                  "dev.ward.application.vulnerability.ScanResult$Located",
                  "dev.ward.application.vulnerability.ScanResult$Unmatchable",
                  "dev.ward.application.vulnerability.VulnerabilityRunMode",
                  "dev.ward.application.vulnerability.VulnerabilityScan",
                  "dev.ward.application.license.LicenseService",
                  "dev.ward.application.license.PackageLicense")),
          Map.entry(
              "init",
              Set.of(
                  "dev.ward.application.project.ProjectDetector",
                  "dev.ward.application.project.ProjectNotFoundException")),
          Map.entry(
              "agent",
              Set.of(
                  "dev.ward.application.project.ProjectDetector",
                  "dev.ward.application.project.ProjectNotFoundException")),
          Map.entry(
              "doctor",
              Set.of(
                  "dev.ward.application.project.ProjectDetector",
                  "dev.ward.application.gate.AnalysisRuleSource",
                  "dev.ward.application.gate.RuleConfigurationCheck",
                  "dev.ward.application.gate.RuleSet")),
          Map.entry("dependency", Set.of()),
          Map.entry(
              "vulnerability",
              Set.of("dev.ward.application.dependency.DependencyInventoryService")),
          Map.entry(
              "license",
              Set.of(
                  "dev.ward.application.dependency.DependencyInventoryService",
                  "dev.ward.application.project.ProjectDetector")),
          Map.entry("project", Set.of()),
          Map.entry("analysis", Set.of()),
          Map.entry("learning", Set.of()),
          Map.entry("update", Set.of()),
          Map.entry(
              "rules",
              Set.of(
                  "dev.ward.application.gate.AnalysisRuleLoad",
                  "dev.ward.application.gate.AnalysisRuleSource",
                  "dev.ward.application.gate.RuleConfigFile",
                  "dev.ward.application.gate.RuleConfigFile$Select",
                  "dev.ward.application.gate.RuleSelection",
                  "dev.ward.application.gate.RuleSelection$Result")));

  private ArchitectureBoundarySupport() {}

  static void assertDomainFeatureEdges(final JavaClasses classes) {
    DOMAIN_FEATURE_EDGES.forEach(
        (sourceFeature, allowedTargets) ->
            assertNoDependencies(
                classes,
                "dev.ward.domain." + sourceFeature + "..",
                target -> {
                  if (!target.startsWith("dev.ward.domain.")) {
                    return false;
                  }
                  final String[] parts = target.split("\\.");
                  return parts.length > FEATURE_SEGMENT
                      && !parts[FEATURE_SEGMENT].equals(sourceFeature)
                      && !allowedTargets.contains(parts[FEATURE_SEGMENT]);
                }));
  }

  static void assertApplicationEntryPoints(final JavaClasses classes) {
    APPLICATION_ENTRY_POINTS.forEach(
        (sourceFeature, allowedTargets) ->
            assertNoDependencies(
                classes,
                "dev.ward.application." + sourceFeature + "..",
                target -> {
                  if (!target.startsWith("dev.ward.application.")) {
                    return false;
                  }
                  final String sourcePrefix = "dev.ward.application." + sourceFeature + ".";
                  return !target.startsWith(sourcePrefix) && !allowedTargets.contains(target);
                }));
  }

  static void assertFeatureAllowlistCoverage(
      final JavaClasses classes, final String layerPrefix, final Set<String> declaredFeatures) {
    final Set<String> discoveredFeatures =
        classes.stream()
            .map(JavaClass::getPackageName)
            .filter(packageName -> packageName.startsWith(layerPrefix))
            .map(packageName -> packageName.substring(layerPrefix.length()).split("\\.")[0])
            .collect(Collectors.toSet());
    assertThat(declaredFeatures)
        .as("documented features for %s", layerPrefix)
        .containsExactlyInAnyOrderElementsOf(discoveredFeatures);
  }

  static boolean isAllowedPackage(final JavaClass javaClass, final Set<String> commonCliTypes) {
    final String packageName = javaClass.getPackageName();
    if (ANALYSIS_PACKAGES.contains(packageName)) {
      return true;
    }
    final String name = javaClass.getName();
    return switch (packageName) {
      case "dev.ward" -> "dev.ward.WardApplication".equals(name);
      case "dev.ward.config" -> isDaggerGenerated(name) || CONFIG_MODULES.contains(name);
      case "dev.ward.infrastructure.cli" ->
          commonCliTypes.contains(javaClass.getSimpleName()) || isDaggerGenerated(name);
      default -> isLayeredFeaturePackage(packageName) || isGeneratedFrontendPackage(packageName);
    };
  }

  private static boolean isLayeredFeaturePackage(final String packageName) {
    return packageName.matches("dev\\.ward\\.(domain|application)\\.[^.]+(\\..*)?")
        || packageName.matches("dev\\.ward\\.infrastructure\\.[^.]+(\\..*)?");
  }

  /**
   * The packages of the frontend module, including its generated parsers, and of the structural
   * rules and dependency summarizers of the Node, Python and Swift languages, and the OpenAPI
   * document model and rules, which are the analysis adapters and domain for those rules.
   */
  private static boolean isGeneratedFrontendPackage(final String packageName) {
    return packageName.matches(
        "dev\\.ward\\.analysis\\.(frontend\\.antlr|domain\\.node|domain\\.jvm|domain\\.python|domain\\.swift|domain\\.openapi|application\\.(?:jvm|node|python|swift|openapi)|infrastructure\\.node)(\\..*)?");
  }

  static boolean isDaggerGenerated(final String name) {
    return name.contains(".Dagger")
        || name.contains("Module_")
        || name.contains("_Factory")
        || name.contains("_") && name.endsWith("Factory")
        || name.contains("_MembersInjector")
        || name.contains("_Proxy");
  }

  static boolean isInnerLayerExternalEffectApi(final String target) {
    return target.startsWith("java.io.")
        || target.startsWith("java.net.")
        || target.startsWith("java.nio.channels.")
        || target.startsWith("java.nio.file.spi.")
        || Set.of(
                "java.lang.Process",
                "java.lang.ProcessBuilder",
                "java.lang.ProcessHandle",
                "java.lang.Runtime",
                "java.nio.file.Files",
                "java.nio.file.FileSystem",
                "java.nio.file.FileSystems",
                "java.nio.file.WatchService")
            .contains(target);
  }

  /**
   * APIs that define or load classes at run time. GraalVM native images contain only the classes
   * seen at build time, so these compile but fail when the executable runs.
   */
  static boolean isNativeImageIncompatibleApi(final String target) {
    return target.startsWith("java.lang.classfile.")
        || Set.of(
                "java.lang.invoke.MethodHandleProxies",
                "java.lang.invoke.MethodHandles$Lookup",
                "java.net.URLClassLoader",
                "java.security.SecureClassLoader")
            .contains(target);
  }

  static boolean isCliExternalEffectApi(final String target) {
    return target.startsWith("java.net.")
        || target.startsWith("java.nio.channels.")
        || target.startsWith("java.nio.file.spi.")
        || Set.of(
                "java.lang.Process",
                "java.lang.ProcessBuilder",
                "java.lang.ProcessHandle",
                "java.lang.Runtime",
                "java.nio.file.Files",
                "java.nio.file.FileSystem",
                "java.nio.file.FileSystems",
                "java.nio.file.WatchService")
            .contains(target);
  }

  static void assertNoDependencies(
      final JavaClasses classes,
      final String sourcePackage,
      final Predicate<String> forbiddenTarget) {
    final ArchRule rule =
        com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses()
            .that()
            .resideInAPackage(sourcePackage)
            .should()
            .dependOnClassesThat(
                new DescribedPredicate<>("match forbidden dependency") {
                  @Override
                  public boolean test(final JavaClass target) {
                    return forbiddenTarget.test(target.getName());
                  }
                });
    rule.check(classes);
  }
}
