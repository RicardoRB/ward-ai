package dev.ward.application.license;

import dev.ward.application.dependency.DependencyInventoryService;
import dev.ward.application.project.ProjectDetector;
import dev.ward.domain.dependency.Dependency;
import dev.ward.domain.dependency.DependencyInventory;
import dev.ward.domain.dependency.DependencyInventoryException;
import dev.ward.domain.dependency.DependencyRelation;
import dev.ward.domain.dependency.DependencyScope;
import dev.ward.domain.dependency.DependencySource;
import dev.ward.domain.dependency.Ecosystem;
import dev.ward.domain.dependency.ModuleDependencies;
import dev.ward.domain.dependency.PackageId;
import dev.ward.domain.license.DeclaredLicense;
import dev.ward.domain.license.LicenseLookup;
import dev.ward.domain.license.LicenseMetadata;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.DiscoveredProject;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.ProjectInspection;
import dev.ward.infrastructure.license.BundledLicenseCatalog;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Fakes for the license use cases: a fixed inventory and metadata by package name. */
public final class LicenseFixtures {
  public static final Project NPM =
      new Project(Path.of("/work/app"), Language.JAVASCRIPT, BuildTool.NPM, Framework.NONE);

  private LicenseFixtures() {}

  /** A dependency of {@code ecosystem} declared in {@code declaredIn}, or transitive when empty. */
  public static Dependency dependency(
      final Ecosystem ecosystem,
      final String name,
      final String version,
      final DependencyScope scope,
      final String declaredIn) {
    return new Dependency(
        new PackageId(ecosystem, name, version),
        declaredIn.isEmpty() ? DependencyRelation.TRANSITIVE : DependencyRelation.DIRECT,
        scope,
        true,
        "package-lock.json",
        declaredIn,
        Set.of(),
        Set.of());
  }

  /** An inventory with one root module of {@code dependencies}. */
  public static DependencyInventory inventory(final Dependency... dependencies) {
    return new DependencyInventory(List.of(new ModuleDependencies("", List.of(dependencies))));
  }

  /** A license lookup of one declared name. */
  public static LicenseLookup declared(final String name) {
    return LicenseLookup.found(DeclaredLicense.of(name), "fake");
  }

  /** A license service whose inventory is {@code inventory} and whose metadata is by name. */
  public static LicenseService service(
      final DependencyInventory inventory, final Map<String, LicenseLookup> byName) {
    return service(project -> inventory, byName);
  }

  /** A license service over {@code source}. */
  public static LicenseService service(
      final DependencySource source, final Map<String, LicenseLookup> byName) {
    final LicenseMetadata metadata =
        (project, modulePath, dependency) ->
            byName.getOrDefault(
                dependency.id().name(),
                LicenseLookup.missing(LicenseLookup.Reason.NOT_INSTALLED, "not installed"));
    final Map<Ecosystem, LicenseMetadata> readers = new EnumMap<>(Ecosystem.class);
    for (final Ecosystem ecosystem : Ecosystem.values()) {
      readers.put(ecosystem, metadata);
    }
    return new LicenseService(
        new DependencyInventoryService(Map.of(BuildTool.NPM, source)),
        readers,
        new BundledLicenseCatalog());
  }

  /** A detector that always finds {@link #NPM}. */
  public static ProjectDetector detector() {
    return new ProjectDetector(
        new ProjectInspection() {
          @Override
          public Optional<Project> detect(final Path start) {
            return Optional.of(NPM);
          }

          @Override
          public List<DiscoveredProject> discover(final Path start) {
            return List.of();
          }
        });
  }

  /** A license service over a mixed inventory: known, dual, excepted, unbundled and unknown. */
  public static LicenseService mixed() {
    return service(
        inventory(
            dependency(
                Ecosystem.NPM, "@scope/pkg", "1.0.0", DependencyScope.RUNTIME, "package.json"),
            dependency(Ecosystem.NPM, "left-pad", "1.3.0", DependencyScope.TEST, ""),
            dependency(Ecosystem.NPM, "mystery", "2.0.0", DependencyScope.RUNTIME, "package.json"),
            dependency(Ecosystem.MAVEN, "com.example:lib", "1.0", DependencyScope.RUNTIME, ""),
            dependency(Ecosystem.MAVEN, "com.example:epl", "1.0", DependencyScope.RUNTIME, "")),
        Map.of(
            "@scope/pkg", declared("Apache-2.0"),
            "left-pad", declared("MIT OR GPL-3.0-only"),
            "com.example:lib", declared("GPL-2.0-only WITH Classpath-exception-2.0"),
            "com.example:epl", declared("Eclipse Public License 1.0")));
  }

  /** A license service whose inventory fails as an unresolvable project does. */
  public static LicenseService failing() {
    return service(
        project -> {
          throw new DependencyInventoryException("/work/app", "lock", "is stale", "npm install");
        },
        Map.of());
  }
}
