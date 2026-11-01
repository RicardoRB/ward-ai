package dev.ward;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import java.util.List;
import java.util.stream.Stream;

/**
 * The toolchain subpackages of init: no toolchain depends on another, and the init roots name a
 * toolchain only in the registry or in the composer the infrastructure root needs.
 */
@AnalyzeClasses(packages = "dev.ward", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureToolchainTest {
  private static final String APPLICATION_INIT = "dev.ward.application.init";
  private static final String INFRASTRUCTURE_INIT = "dev.ward.infrastructure.init";
  private static final List<String> TOOLCHAINS =
      List.of("jvm", "node", "python", "swift", "gradle", "maven");

  /**
   * The root classes that may name a toolchain: the registry, and the composers that Dagger wires
   * with a toolchain's generator or installer as a constructor argument.
   */
  private static final List<String> ROOT_ALLOWLIST =
      List.of(
          "InitToolchains",
          "JvmBuildToolConfigurator",
          "CompositeToolInstaller",
          "ToolchainArchitectureRules");

  @ArchTest
  static void toolchainSubpackagesDoNotDependOnEachOther(final JavaClasses production) {
    checkToolchainsStayIndependent(production);
  }

  @ArchTest
  static void initRootsReachToolchainsOnlyThroughTheRegistry(final JavaClasses production) {
    checkRootsReachToolchainsOnlyThroughTheRegistry(production);
  }

  /** Checks that no toolchain's classes depend on another toolchain's classes. */
  static void checkToolchainsStayIndependent(final JavaClasses classes) {
    for (final String from : TOOLCHAINS) {
      for (final String to : TOOLCHAINS) {
        if (!from.equals(to)) {
          noClasses()
              .that()
              .resideInAnyPackage(packages(from))
              .should()
              .dependOnClassesThat()
              .resideInAnyPackage(packages(to))
              .check(classes);
        }
      }
    }
  }

  /** Checks that the init roots name a toolchain only in the allowlisted root classes. */
  static void checkRootsReachToolchainsOnlyThroughTheRegistry(final JavaClasses classes) {
    noClasses()
        .that()
        .resideInAnyPackage(APPLICATION_INIT, INFRASTRUCTURE_INIT)
        .and(notAllowlisted())
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(toolchainPackages())
        .check(classes);
  }

  /**
   * Dagger's generated factories name a constructor's parameter types, so they are not authored.
   */
  private static DescribedPredicate<JavaClass> notAllowlisted() {
    return DescribedPredicate.describe(
        "not in the root allowlist",
        type ->
            !ROOT_ALLOWLIST.contains(type.getSimpleName()) && !type.getSimpleName().contains("_"));
  }

  private static String[] toolchainPackages() {
    return TOOLCHAINS.stream()
        .flatMap(toolchain -> Stream.of(packages(toolchain)))
        .toArray(String[]::new);
  }

  /** The application and infrastructure packages of {@code toolchain}. */
  private static String[] packages(final String toolchain) {
    return new String[] {
      APPLICATION_INIT + "." + toolchain + "..", INFRASTRUCTURE_INIT + "." + toolchain + ".."
    };
  }
}
