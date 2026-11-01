package dev.ward.infrastructure.init.node;

import dev.ward.domain.init.CompatibilityFinding;
import dev.ward.domain.init.CompatibilityOutcome;
import dev.ward.domain.project.PackageManifest;
import dev.ward.domain.project.Project;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Checks the packages init adds against the project's Node major version, and {@code
 * typescript-eslint} against its TypeScript version. A Node or TypeScript version cannot be fixed
 * by choosing another package version, so findings have no approval path.
 *
 * @param findings the {@code INCOMPATIBLE} findings, one per package
 * @param notes plan notes, such as a minimum minor Node version a range needs
 */
record NodeCompatibility(List<CompatibilityFinding> findings, List<String> notes) {
  private static final Logger LOGGER = Logger.getLogger(NodeCompatibility.class.getName());
  private static final String SVELTE_CHECK = "svelte-check";
  private static final String SVELTE_KIT = "@sveltejs/kit";
  private static final String SVELTE_SYNC = "svelte-kit sync";

  NodeCompatibility {
    findings = List.copyOf(findings);
    notes = List.copyOf(notes);
  }

  /**
   * Checks {@code added}, the packages init would add to {@code project}, a declared Biome or
   * ESLint when {@code gates} selects it, and the Vitest the {@code vitest} gate runs against the
   * project's Vite.
   */
  static NodeCompatibility check(
      final Project project,
      final PackageManifest manifest,
      final List<NodePackageCatalog.Package> added,
      final Set<String> gates) {
    final int node = Integer.parseInt(project.languageVersion());
    final List<CompatibilityFinding> findings = new ArrayList<>();
    final List<String> notes = new ArrayList<>();
    for (final NodePackageCatalog.Package tool : added) {
      checkNode(tool, node, findings, notes);
      typescript(tool, manifest, added).ifPresent(findings::add);
    }
    if (gates.contains("biome")) {
      declaredBiome(manifest).ifPresent(findings::add);
    }
    if (gates.contains("eslint")) {
      declaredEslint(manifest).ifPresent(findings::add);
    }
    vitestVite(manifest, gates).ifPresent(findings::add);
    findings.addAll(declaredCheckers(manifest));
    findings.addAll(NodeAngularCompatibility.check(node, manifest, added));
    svelteKitSync(manifest, gates).ifPresent(notes::add);
    findings.forEach(
        finding ->
            LOGGER.log(
                Level.INFO,
                () ->
                    "event=node-compatibility outcome="
                        + finding.outcome()
                        + " tool="
                        + finding.artifactId()));
    return new NodeCompatibility(findings, notes);
  }

  private static void checkNode(
      final NodePackageCatalog.Package tool,
      final int node,
      final List<CompatibilityFinding> findings,
      final List<String> notes) {
    final Optional<String> minimum = NodeVersionRange.nodeMinimum(tool.node(), node);
    if (minimum.isEmpty()) {
      if (!NodePackageCatalog.ANGULAR_ESLINT.equals(tool.name())) {
        findings.add(
            finding(
                tool,
                "Node " + node,
                tool.name()
                    + " "
                    + tool.version()
                    + " supports Node "
                    + tool.node()
                    + "; the project uses Node "
                    + node
                    + "."));
      }
    } else if (minimum.get().startsWith(node + ".") && !minimum.get().endsWith(".0.0")) {
      notes.add(tool.name() + " needs Node " + minimum.get() + " or later.");
    }
  }

  private static Optional<CompatibilityFinding> typescript(
      final NodePackageCatalog.Package tool,
      final PackageManifest manifest,
      final List<NodePackageCatalog.Package> added) {
    if (tool.typescript() == null) {
      return Optional.empty();
    }
    final Optional<String> version =
        manifest.declares(NodePackageCatalog.TYPESCRIPT)
            ? NodeVersionRange.lowerBound(
                manifest.dependencies().get(NodePackageCatalog.TYPESCRIPT))
            : added.stream()
                .filter(candidate -> NodePackageCatalog.TYPESCRIPT.equals(candidate.name()))
                .map(NodePackageCatalog.Package::version)
                .findFirst();
    return version
        .filter(declared -> !NodeVersionRange.matches(tool.typescript(), declared))
        .map(
            declared ->
                finding(
                    tool,
                    "TypeScript " + declared,
                    tool.name()
                        + " "
                        + tool.version()
                        + " supports TypeScript "
                        + tool.typescript()
                        + "; the project uses TypeScript "
                        + declared
                        + "."));
  }

  /** A declared Biome must start inside the range Ward's checkstyle reading supports. */
  private static Optional<CompatibilityFinding> declaredBiome(final PackageManifest manifest) {
    final String declared = manifest.dependencies().get(NodePackageCatalog.BIOME_PACKAGE);
    if (declared == null) {
      return Optional.empty();
    }
    return NodeVersionRange.lowerBound(declared)
        .filter(version -> !NodeVersionRange.matches(NodePackageCatalog.BIOME_SUPPORTED, version))
        .map(
            version ->
                new CompatibilityFinding(
                    NodePackageCatalog.BIOME_PACKAGE,
                    "Biome " + version,
                    NodePackageCatalog.BIOME_VERSION,
                    CompatibilityOutcome.INCOMPATIBLE,
                    NodePackageCatalog.BIOME_PACKAGE
                        + " "
                        + declared
                        + " is outside the supported range "
                        + NodePackageCatalog.BIOME_SUPPORTED
                        + "."));
  }

  /** Each declared framework checker must start inside the range its gate supports. */
  private static List<CompatibilityFinding> declaredCheckers(final PackageManifest manifest) {
    return NodePackageCatalog.CHECKER_SUPPORTED.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .flatMap(
            checker -> declaredChecker(manifest, checker.getKey(), checker.getValue()).stream())
        .toList();
  }

  private static Optional<CompatibilityFinding> declaredChecker(
      final PackageManifest manifest, final String name, final String supported) {
    final String declared = manifest.dependencies().get(name);
    if (declared == null) {
      return Optional.empty();
    }
    return NodeVersionRange.lowerBound(declared)
        .filter(version -> !NodeVersionRange.matches(supported, version))
        .map(
            version ->
                new CompatibilityFinding(
                    name,
                    name + " " + version,
                    NodePackageCatalog.find(name).orElseThrow().version(),
                    CompatibilityOutcome.INCOMPATIBLE,
                    name + " " + declared + " is outside the supported range " + supported + "."));
  }

  /**
   * Checks the Vitest the {@code vitest} gate runs against the project's Vite. Ward's pins are the
   * only Vitest releases with a recorded Vite range, so other minors are not checked.
   */
  private static Optional<CompatibilityFinding> vitestVite(
      final PackageManifest manifest, final Set<String> gates) {
    if (!gates.contains(NodePackageCatalog.VITEST) || !manifest.declares(NodePackageCatalog.VITE)) {
      return Optional.empty();
    }
    return NodeVersionRange.lowerBound(manifest.dependencies().get(NodePackageCatalog.VITE))
        .flatMap(
            vite ->
                manifest.declares(NodePackageCatalog.VITEST)
                    ? declaredVitestVite(manifest, vite)
                    : undeclaredVitestVite(vite));
  }

  /** A declared Vitest must support the project's Vite, as its pinned release declares. */
  private static Optional<CompatibilityFinding> declaredVitestVite(
      final PackageManifest manifest, final String vite) {
    final String declared = manifest.dependencies().get(NodePackageCatalog.VITEST);
    return NodeVersionRange.lowerBound(declared)
        .flatMap(NodeVitestReleases::onMinor)
        .filter(release -> !NodeVersionRange.matches(release.vite(), vite))
        .map(
            release ->
                new CompatibilityFinding(
                    NodePackageCatalog.VITEST,
                    "Vite " + vite,
                    release.version(),
                    CompatibilityOutcome.INCOMPATIBLE,
                    NodePackageCatalog.VITEST
                        + " "
                        + declared
                        + " supports Vite "
                        + release.vite()
                        + "; the project uses Vite "
                        + vite
                        + "."));
  }

  /** Without a declared Vitest, some pinned release must support the project's Vite. */
  private static Optional<CompatibilityFinding> undeclaredVitestVite(final String vite) {
    if (NodeVitestReleases.forVite(vite).isPresent()) {
      return Optional.empty();
    }
    return Optional.of(
        new CompatibilityFinding(
            NodePackageCatalog.VITEST,
            "Vite " + vite,
            null,
            CompatibilityOutcome.INCOMPATIBLE,
            "No pinned "
                + NodePackageCatalog.VITEST
                + " supports Vite "
                + vite
                + "; the newest supports "
                + NodeVitestReleases.newestViteRange()
                + "."));
  }

  /** A declared ESLint must start inside the range the generated config supports. */
  private static Optional<CompatibilityFinding> declaredEslint(final PackageManifest manifest) {
    final String declared = manifest.dependencies().get(NodePackageCatalog.ESLINT);
    if (declared == null) {
      return Optional.empty();
    }
    return NodeVersionRange.lowerBound(declared)
        .filter(version -> !reachesSupportedEslint(declared, version))
        .map(
            version ->
                new CompatibilityFinding(
                    NodePackageCatalog.ESLINT,
                    "ESLint " + version,
                    NodePackageCatalog.ESLINT_VERSION,
                    CompatibilityOutcome.INCOMPATIBLE,
                    NodePackageCatalog.ESLINT
                        + " "
                        + declared
                        + " is outside the supported range "
                        + NodePackageCatalog.ESLINT_SUPPORTED
                        + "."));
  }

  /**
   * Returns whether {@code declared} can resolve inside the supported ESLint range. A caret range
   * such as {@code ^9} resolves to the newest release of its major, so it reaches the range when
   * that major has a supported release; the top of the major stands for all of it.
   */
  private static boolean reachesSupportedEslint(final String declared, final String lowerBound) {
    if (NodeVersionRange.matches(NodePackageCatalog.ESLINT_SUPPORTED, lowerBound)) {
      return true;
    }
    final String major = lowerBound.substring(0, lowerBound.indexOf('.'));
    return declared.startsWith("^")
        && NodeVersionRange.matches(NodePackageCatalog.ESLINT_SUPPORTED, major + ".999.999");
  }

  /**
   * A SvelteKit project's {@code svelte-check} reads {@code .svelte-kit/tsconfig.json}, which only
   * {@code svelte-kit sync} writes. The note does not block init, because the project may sync in
   * another step, such as CI.
   */
  private static Optional<String> svelteKitSync(
      final PackageManifest manifest, final Set<String> gates) {
    if (!gates.contains(SVELTE_CHECK) || !manifest.declares(SVELTE_KIT)) {
      return Optional.empty();
    }
    final String prepare = manifest.prepare();
    if (prepare != null && prepare.contains(SVELTE_SYNC)) {
      return Optional.empty();
    }
    return Optional.of(
        SVELTE_CHECK
            + " needs "
            + SVELTE_SYNC
            + ": add \"prepare\": \"svelte-kit sync || echo ''\" to package.json scripts.");
  }

  private static CompatibilityFinding finding(
      final NodePackageCatalog.Package tool, final String current, final String message) {
    return new CompatibilityFinding(
        tool.name(), current, tool.version(), CompatibilityOutcome.INCOMPATIBLE, message);
  }

  /** Returns whether any finding blocks init. */
  boolean incompatible() {
    return !findings.isEmpty();
  }

  /** Returns the findings' messages, joined for an error. */
  String message() {
    return String.join(" ", findings.stream().map(CompatibilityFinding::message).toList());
  }
}
