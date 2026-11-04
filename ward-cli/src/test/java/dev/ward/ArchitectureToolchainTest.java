package dev.ward;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The toolchain slices of every feature, {@code dev.ward.<layer>.<feature>.<toolchain>..}: no slice
 * depends on another, except that the JVM build tools may use the shared JVM code. The feature
 * roots of the application and infrastructure layers name a toolchain only in an allowlisted class.
 */
final class ArchitectureToolchainTest {
  private static final String JVM = "jvm";
  private static final List<String> TOOLCHAINS =
      List.of(JVM, "node", "python", "swift", "gradle", "maven");
  private static final List<String> JVM_BUILD_TOOLS = List.of("gradle", "maven");

  /**
   * The root classes that may name a toolchain, each with the reason it may. Dagger-generated
   * factories name a constructor's parameter types, so they are not authored and are not checked.
   */
  private static final Map<String, String> ROOT_ALLOWLIST =
      Map.ofEntries(
          Map.entry("InitToolchains", "the init registry that maps each toolchain to its support"),
          Map.entry(
              "JvmBuildToolConfigurator",
              "routes JVM init mutations to the Maven or Gradle adapter"),
          Map.entry(
              "CompositeToolInstaller",
              "routes tool installation to the adapter of each toolchain"),
          Map.entry(
              "ToolchainArchitectureRules", "generates each toolchain's architecture test config"),
          Map.entry(
              "FileSystemProjectInspection",
              "detects a directory's project with each toolchain's check"),
          Map.entry(
              "RepositoryProjectScan",
              "collects the project roots that each toolchain's check finds"),
          Map.entry(
              "BuildModules",
              "tells whether a directory is a module of a Maven, Gradle, Node or uv root"),
          Map.entry(
              "OwnLanguageMembers",
              "picks the Python project of a Node workspace member that lists it"),
          Map.entry("ToolchainFindingSources", "registers the finding source of each toolchain"),
          Map.entry(
              "CompositeFindingExtractor",
              "routes each gate to its finding source, wrapping Turborepo workspaces"),
          Map.entry("BuildToolFindings", "routes each toolchain to its doctor findings"),
          Map.entry(
              "ProjectDependencyAdapters",
              "registers the layout reader and resolver of each toolchain that has dependency edges"),
          Map.entry(
              "QualityGateFactory",
              "maps each gate id to its implementation, including the Maven and Swift gates"),
          Map.entry(
              "DoctorService", "the doctor facade, which runs the JVM dependency-check diagnosis"),
          Map.entry(
              "SystemEnvironmentInspection",
              "reads each toolchain's environment facts for the doctor"));

  /**
   * The feature roots: each feature's classes outside its subpackages. The CLI adapters under
   * {@code infrastructure.cli} are not checked here; they are bound by the CLI adapter rule
   * instead.
   */
  private static final String[] ROOT_PACKAGES = {
    "dev.ward.application.*", "dev.ward.infrastructure.*"
  };

  private ArchitectureToolchainTest() {}

  @Test
  void toolchainSlicesDoNotDependOnEachOther() {
    final JavaClasses classes = production();
    for (final String toolchain : TOOLCHAINS) {
      assertThat(classes.stream().map(JavaClass::getPackageName))
          .as("classes of the %s slice", toolchain)
          .anyMatch(name -> inSlice(name, toolchain));
    }
    checkToolchainsStayIndependent(classes);
  }

  @Test
  void featureRootsReachToolchainsOnlyThroughTheAllowlist() {
    checkRootsReachToolchainsOnlyThroughTheRegistry(production());
  }

  private static JavaClasses production() {
    return new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("dev.ward");
  }

  /**
   * Checks that no toolchain's slice depends on another toolchain's slice, in any feature or layer.
   * A fixture may hold no class of some toolchain, so an empty slice passes here.
   */
  static void checkToolchainsStayIndependent(final JavaClasses classes) {
    for (final String from : TOOLCHAINS) {
      for (final String to : TOOLCHAINS) {
        if (!mayDependOn(from, to)) {
          noClasses()
              .that()
              .resideInAnyPackage(slice(from))
              .should()
              .dependOnClassesThat()
              .resideInAnyPackage(slice(to))
              .allowEmptyShould(true)
              .check(classes);
        }
      }
    }
  }

  /**
   * Checks that the feature roots of the application and infrastructure layers name a toolchain
   * only in the allowlisted classes. Domain toolchain packages are shared vocabulary, so they are
   * not checked.
   */
  static void checkRootsReachToolchainsOnlyThroughTheRegistry(final JavaClasses classes) {
    noClasses()
        .that()
        .resideInAnyPackage(ROOT_PACKAGES)
        .and(notAllowlisted())
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(applicationAndInfrastructureToolchainPackages())
        .check(classes);
  }

  private static boolean inSlice(final String packageName, final String toolchain) {
    return packageName.matches("dev\\.ward\\.[^.]+\\.[^.]+\\." + toolchain + "(\\..*)?");
  }

  private static boolean mayDependOn(final String from, final String to) {
    return from.equals(to) || (JVM.equals(to) && JVM_BUILD_TOOLS.contains(from));
  }

  private static DescribedPredicate<JavaClass> notAllowlisted() {
    return DescribedPredicate.describe(
        "not in the root allowlist",
        type ->
            !ROOT_ALLOWLIST.containsKey(type.getSimpleName())
                && !type.getSimpleName().contains("_"));
  }

  /**
   * The packages of {@code toolchain} in every layer and feature, such as {@code
   * dev.ward.*.*.node..}.
   */
  private static String[] slice(final String toolchain) {
    return new String[] {"dev.ward.*.*." + toolchain + ".."};
  }

  private static String[] applicationAndInfrastructureToolchainPackages() {
    return TOOLCHAINS.stream()
        .flatMap(
            toolchain ->
                Stream.of(
                    "dev.ward.application.*." + toolchain + "..",
                    "dev.ward.infrastructure.*." + toolchain + ".."))
        .toArray(String[]::new);
  }
}
