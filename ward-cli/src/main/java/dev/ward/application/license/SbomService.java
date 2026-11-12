package dev.ward.application.license;

import dev.ward.application.project.ProjectDetector;
import dev.ward.domain.dependency.Dependency;
import dev.ward.domain.license.LicenseExpression;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Named;

/**
 * Writes the CycloneDX 1.6 JSON software bill of materials of a project from its dependency
 * inventory and the license lookup. The document has no serial number and no timestamp and sorts
 * components by package URL, so the same inputs give the same bytes. Transitive packages are
 * components without outgoing edges, because the inventory records no edges between packages.
 */
public final class SbomService {
  private static final String SPEC_VERSION = "1.6";
  private static final String REF = "bom-ref";
  private static final String NAME = "name";
  private static final String VERSION = "version";
  private static final String ROOT_REF = "root";

  private final ProjectDetector detector;
  private final LicenseService licenses;
  private final String wardVersion;

  /** Creates the service. */
  @Inject
  public SbomService(
      final ProjectDetector detector,
      final LicenseService licenses,
      @Named("ward.version") final String wardVersion) {
    this.detector = detector;
    this.licenses = licenses;
    this.wardVersion = wardVersion;
  }

  /**
   * Returns the SBOM of the project in {@code directory}.
   *
   * @param scopes the lower-case scope names to include; empty means every scope
   * @throws dev.ward.domain.config.WardException if there is no project or its inventory fails
   */
  public Document generate(final Path directory, final Set<String> scopes) {
    final Project project = Projects.detect(detector, directory);
    final List<PackageLicense> all =
        licenses.licenses(project, scopes).stream()
            .sorted(Comparator.comparing(entry -> entry.dependency().purl()))
            .toList();
    final List<String> warnings = new ArrayList<>();
    final List<Object> components = new ArrayList<>();
    final List<Object> dependencies = new ArrayList<>();
    final List<String> direct = new ArrayList<>();
    dependencies.add(null);
    for (final PackageLicense entry : all) {
      final Dependency dependency = entry.dependency();
      components.add(component(entry));
      dependencies.add(edge(dependency.purl(), List.of()));
      if (dependency.direct()) {
        direct.add(dependency.purl());
      }
      if (!entry.known()) {
        warnings.add(
            "ward sbom: "
                + dependency.purl()
                + " has an unknown license: "
                + entry.unknownReason());
      }
    }
    dependencies.set(0, edge(ROOT_REF, direct));
    final Map<String, Object> bom = new LinkedHashMap<>();
    bom.put("bomFormat", "CycloneDX");
    bom.put("specVersion", SPEC_VERSION);
    bom.put(VERSION, 1);
    bom.put("metadata", metadata(directory));
    bom.put("components", components);
    bom.put("dependencies", dependencies);
    return new Document(JsonTree.write(bom), warnings);
  }

  private Map<String, Object> metadata(final Path directory) {
    final Map<String, Object> tool = new LinkedHashMap<>();
    tool.put("type", "application");
    tool.put(NAME, "ward");
    tool.put(VERSION, wardVersion);
    final Map<String, Object> root = new LinkedHashMap<>();
    root.put("type", "application");
    root.put(REF, ROOT_REF);
    root.put(NAME, String.valueOf(directory.toAbsolutePath().normalize().getFileName()));
    final Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("tools", Map.of("components", List.of(tool)));
    metadata.put("component", root);
    return metadata;
  }

  private static Map<String, Object> edge(final String ref, final List<String> dependsOn) {
    final Map<String, Object> edge = new LinkedHashMap<>();
    edge.put("ref", ref);
    edge.put("dependsOn", dependsOn);
    return edge;
  }

  private static Map<String, Object> component(final PackageLicense entry) {
    final Dependency dependency = entry.dependency();
    final String name = dependency.id().name();
    final Map<String, Object> component = new LinkedHashMap<>();
    component.put("type", "library");
    component.put(REF, dependency.purl());
    final int separator = groupSeparator(dependency);
    if (separator > 0) {
      component.put("group", name.substring(0, separator));
    }
    component.put(NAME, separator > 0 ? name.substring(separator + 1) : name);
    component.put(VERSION, dependency.id().version());
    component.put("purl", dependency.purl());
    entry
        .expression()
        .ifPresent(expression -> component.put("licenses", List.of(license(expression))));
    return component;
  }

  /** Returns where the group ends in the package name, or -1 when the package has no group. */
  private static int groupSeparator(final Dependency dependency) {
    final String name = dependency.id().name();
    return switch (dependency.id().ecosystem()) {
      case MAVEN -> name.indexOf(':');
      case NPM -> name.startsWith("@") ? name.indexOf('/') : -1;
      default -> -1;
    };
  }

  private static Map<String, Object> license(final LicenseExpression expression) {
    if (expression instanceof LicenseExpression.Term term && term.exception() == null) {
      final boolean reference = term.id().contains("LicenseRef-");
      return Map.of("license", Map.of(reference ? NAME : "id", term.id()));
    }
    return Map.of("expression", expression.render());
  }
}
