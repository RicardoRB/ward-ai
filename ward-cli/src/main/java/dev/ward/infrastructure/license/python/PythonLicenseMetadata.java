package dev.ward.infrastructure.license.python;

import dev.ward.domain.dependency.Dependency;
import dev.ward.domain.license.DeclaredLicense;
import dev.ward.domain.license.LicenseCatalog;
import dev.ward.domain.license.LicenseLookup;
import dev.ward.domain.license.LicenseMetadata;
import dev.ward.domain.license.LicenseNormalizer;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.dependency.LockFiles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Reads a Python package's license from the metadata that its installer left in the project's
 * virtual environment ({@code .venv} or {@code venv} in the member directory or the project root,
 * or the directory named by {@code VIRTUAL_ENV}): {@code METADATA} of a {@code .dist-info} or
 * {@code PKG-INFO} of an {@code .egg-info}. The order of preference is {@code License-Expression},
 * a short recognizable {@code License}, then {@code License ::} classifiers. Ward never runs pip or
 * uv, and uses a file only when its {@code Version} equals the inventory's.
 */
public final class PythonLicenseMetadata implements LicenseMetadata {
  private static final int MAX_LICENSE_FIELD = 100;
  private static final String CLASSIFIER_PREFIX = "License :: ";
  private static final String OSI_PREFIX = "OSI Approved :: ";
  private static final List<String> ENVIRONMENTS = List.of(".venv", "venv");

  private final LicenseCatalog catalog;
  private final LicenseNormalizer normalizer;
  private final String virtualEnv;

  /**
   * Creates the reader.
   *
   * @param catalog the bundled SPDX data
   * @param virtualEnv the value of {@code VIRTUAL_ENV}, or {@code null} when it is not set
   */
  public PythonLicenseMetadata(final LicenseCatalog catalog, final String virtualEnv) {
    this.catalog = catalog;
    this.normalizer = new LicenseNormalizer(catalog);
    this.virtualEnv = virtualEnv;
  }

  @Override
  public LicenseLookup lookup(
      final Project project, final String modulePath, final Dependency dependency) {
    final String wanted = normalize(dependency.id().name());
    final String version = dependency.id().version();
    boolean found = false;
    for (final Path site : sitePackages(project.root(), modulePath)) {
      for (final Path info : metadataFiles(site, wanted)) {
        final Fields fields = Fields.read(info);
        if (version.equals(fields.first("Version"))) {
          return declared(fields);
        }
        found = true;
      }
    }
    return found
        ? LicenseLookup.missing(
            LicenseLookup.Reason.VERSION_MISMATCH, "the installed version differs from " + version)
        : LicenseLookup.missing(
            LicenseLookup.Reason.NOT_INSTALLED,
            dependency.id().name() + " is not installed in a virtual environment");
  }

  private LicenseLookup declared(final Fields fields) {
    final String expression = fields.first("License-Expression");
    if (!expression.isEmpty()) {
      return LicenseLookup.found(DeclaredLicense.of(expression), "metadata");
    }
    final String license = fields.first("License");
    if (!license.isEmpty()
        && license.length() <= MAX_LICENSE_FIELD
        && !license.contains("\n")
        && normalizer.normalize(DeclaredLicense.of(license)).isPresent()) {
      return LicenseLookup.found(DeclaredLicense.of(license), "metadata");
    }
    final List<DeclaredLicense.Entry> classified = new ArrayList<>();
    for (final String classifier : fields.all("Classifier")) {
      classifier(classifier).ifPresent(id -> classified.add(new DeclaredLicense.Entry(id, "")));
    }
    return classified.isEmpty()
        ? LicenseLookup.missing(
            LicenseLookup.Reason.NO_LICENSE_DECLARED,
            "the metadata names no license Ward recognizes")
        : LicenseLookup.found(new DeclaredLicense(classified), "metadata");
  }

  private Optional<String> classifier(final String classifier) {
    if (!classifier.startsWith(CLASSIFIER_PREFIX)) {
      return Optional.empty();
    }
    final String name = classifier.substring(CLASSIFIER_PREFIX.length());
    return catalog
        .classifier(name.startsWith(OSI_PREFIX) ? name.substring(OSI_PREFIX.length()) : name)
        .or(() -> catalog.classifier(name));
  }

  private List<Path> sitePackages(final Path root, final String modulePath) {
    final List<Path> environments = new ArrayList<>();
    final Path member = root.resolve(modulePath);
    for (final String name : ENVIRONMENTS) {
      environments.add(member.resolve(name));
      environments.add(root.resolve(name));
    }
    if (virtualEnv != null && !virtualEnv.isBlank()) {
      environments.add(Path.of(virtualEnv));
    }
    final List<Path> sites = new ArrayList<>();
    for (final Path environment : environments.stream().distinct().toList()) {
      sites.addAll(sitesOf(environment));
    }
    return sites;
  }

  private static List<Path> sitesOf(final Path environment) {
    final List<Path> sites = new ArrayList<>();
    final Path windows = environment.resolve("Lib").resolve("site-packages");
    if (Files.isDirectory(windows)) {
      sites.add(windows);
    }
    final Path lib = environment.resolve("lib");
    if (Files.isDirectory(lib)) {
      try (Stream<Path> children = Files.list(lib)) {
        children
            .filter(path -> LockFiles.fileName(path).startsWith("python"))
            .map(path -> path.resolve("site-packages"))
            .filter(Files::isDirectory)
            .sorted()
            .forEach(sites::add);
      } catch (IOException exception) {
        return sites;
      }
    }
    return sites;
  }

  /** Returns the metadata files in {@code site} of the package called {@code wanted}. */
  private static List<Path> metadataFiles(final Path site, final String wanted) {
    try (Stream<Path> entries = Files.list(site)) {
      return entries
          .sorted()
          .map(entry -> metadataFile(entry, wanted))
          .flatMap(Optional::stream)
          .toList();
    } catch (IOException exception) {
      return List.of();
    }
  }

  private static Optional<Path> metadataFile(final Path entry, final String wanted) {
    final String name = LockFiles.fileName(entry);
    final boolean distInfo = name.endsWith(".dist-info");
    final boolean eggInfo = name.endsWith(".egg-info");
    if (!distInfo && !eggInfo) {
      return Optional.empty();
    }
    final String stem = name.substring(0, name.lastIndexOf('.'));
    final int dash = stem.indexOf('-');
    if (dash <= 0 || !normalize(stem.substring(0, dash)).equals(wanted)) {
      return Optional.empty();
    }
    final Path file = entry.resolve(distInfo ? "METADATA" : "PKG-INFO");
    return Files.isRegularFile(file) ? Optional.of(file) : Optional.empty();
  }

  /** Normalizes a project name as PEP 503 does. */
  private static String normalize(final String name) {
    return name.toLowerCase(Locale.ROOT).replaceAll("[-_.]+", "-");
  }

  /** The header fields of a metadata file: the lines before the first blank line. */
  private record Fields(List<String[]> pairs) {
    static Fields read(final Path file) {
      final List<String[]> pairs = new ArrayList<>();
      try {
        for (final String line : Files.readAllLines(file)) {
          if (line.isBlank()) {
            break;
          }
          final int colon = line.indexOf(':');
          if (colon > 0 && !Character.isWhitespace(line.charAt(0))) {
            pairs.add(new String[] {line.substring(0, colon), line.substring(colon + 1).trim()});
          }
        }
      } catch (IOException exception) {
        return new Fields(List.of());
      }
      return new Fields(pairs);
    }

    String first(final String key) {
      return all(key).stream().findFirst().orElse("");
    }

    List<String> all(final String key) {
      return pairs.stream()
          .filter(pair -> pair[0].equalsIgnoreCase(key))
          .map(pair -> pair[1])
          .toList();
    }
  }
}
