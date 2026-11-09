package dev.ward.analysis.domain.rule.check;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The frameworks whose conventional entry files Ward does not model. A workspace that depends on
 * one is silent for unused files, since its pages, routes or components are entries by convention.
 * Each row names the knip plugin that would model it, so the row can be removed when that plugin is
 * ported.
 */
final class NodeFrameworks {

  /**
   * Package names whose workspace is silent for unused files, mapped to the knip plugin they need.
   */
  private static final Map<String, String> PACKAGES =
      Map.of(
          "next", "knip next plugin: pages, app routes and middleware",
          "nuxt", "knip nuxt plugin: pages, layouts and server routes",
          "astro", "knip astro plugin: pages and content collections",
          "@sveltejs/kit", "knip sveltekit plugin: routes and hooks",
          "@angular/core", "knip angular plugin: modules, components and routes",
          "gatsby", "knip gatsby plugin: pages and gatsby-* files",
          "@nestjs/core", "knip nestjs plugin: modules and controllers",
          "vite", "knip vite plugin: index.html entries and the config file");

  /**
   * Package name prefixes whose workspace is silent, with the same meaning as {@link #PACKAGES}.
   */
  private static final Map<String, String> PREFIXES =
      Map.of("@remix-run/", "knip remix plugin: routes and root files");

  private static final List<String> SECTIONS =
      List.of("dependencies", "devDependencies", "peerDependencies", "optionalDependencies");

  private NodeFrameworks() {}

  /** Whether the manifest depends on a framework listed in this table. */
  static boolean isFrameworkWorkspace(final NodeManifests.Manifest owner) {
    return SECTIONS.stream()
        .flatMap(section -> owner.manifest().entries(section).stream())
        .anyMatch(dependency -> framework(dependency.key()).isPresent());
  }

  private static Optional<String> framework(final String name) {
    return Optional.ofNullable(PACKAGES.get(name))
        .or(
            () ->
                PREFIXES.entrySet().stream()
                    .filter(prefix -> name.startsWith(prefix.getKey()))
                    .map(Map.Entry::getValue)
                    .findFirst());
  }
}
