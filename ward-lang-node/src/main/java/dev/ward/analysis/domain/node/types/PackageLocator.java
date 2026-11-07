package dev.ward.analysis.domain.node.types;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the declaration file of a module specifier: next to the importer or in {@code
 * node_modules}.
 */
final class PackageLocator {
  private static final String NODE_MODULES = "node_modules";
  private static final Pattern ENTRY_FIELD =
      Pattern.compile("\"(?:types|typings)\"\\s*:\\s*\"([^\"]+)\"");
  private static final Pattern MAIN_FIELD = Pattern.compile("\"main\"\\s*:\\s*\"([^\"]+)\"");
  private static final List<String> SUFFIXES =
      List.of(".d.ts", ".ts", ".tsx", "/index.d.ts", "/index.ts", ".d.mts", ".d.cts");

  private final DeclarationFiles files;

  PackageLocator(final DeclarationFiles files) {
    this.files = files;
  }

  Optional<String> relative(final String from, final String specifier) {
    final String directory = from.contains("/") ? from.substring(0, from.lastIndexOf('/') + 1) : "";
    final String base = Paths.normalize(directory + specifier);
    return firstExisting(base);
  }

  Optional<String> firstExisting(final String base) {
    if (files.exists(base) && base.matches(".*\\.[cm]?[jt]sx?$")) {
      return Optional.of(base);
    }
    for (final String suffix : SUFFIXES) {
      if (files.exists(base + suffix)) {
        return Optional.of(base + suffix);
      }
    }
    return Optional.empty();
  }

  /** Finds the declaration file of a package under the nearest {@code node_modules}. */
  Optional<String> installed(final String from, final String specifier) {
    final String[] segments = specifier.split("/");
    final int length = specifier.startsWith("@") ? 2 : 1;
    if (segments.length < length) {
      return Optional.empty();
    }
    final String name = String.join("/", List.of(segments).subList(0, length));
    final String subpath = String.join("/", List.of(segments).subList(length, segments.length));
    String directory = from.contains("/") ? from.substring(0, from.lastIndexOf('/')) : "";
    while (true) {
      final String root = directory.isEmpty() ? NODE_MODULES : directory + "/" + NODE_MODULES;
      final Optional<String> own = entry(root + "/" + name, subpath);
      if (own.isPresent()) {
        return own;
      }
      final Optional<String> types = entry(root + "/@types/" + typesName(name), subpath);
      if (types.isPresent()) {
        return types;
      }
      if (directory.isEmpty()) {
        return Optional.empty();
      }
      directory = directory.contains("/") ? directory.substring(0, directory.lastIndexOf('/')) : "";
    }
  }

  private Optional<String> entry(final String packageDirectory, final String subpath) {
    if (!files.exists(packageDirectory + "/package.json")
        && !files.exists(packageDirectory + "/index.d.ts")
        && subpath.isEmpty()) {
      return Optional.empty();
    }
    if (!subpath.isEmpty()) {
      return firstExisting(packageDirectory + "/" + subpath);
    }
    final String manifest = files.read(packageDirectory + "/package.json").orElse("");
    final Matcher types = ENTRY_FIELD.matcher(manifest);
    if (types.find()) {
      final String named = Paths.normalize(packageDirectory + "/" + types.group(1));
      if (files.exists(named)) {
        return Optional.of(named);
      }
    }
    final Matcher main = MAIN_FIELD.matcher(manifest);
    if (main.find()) {
      final String stem =
          Paths.normalize(packageDirectory + "/" + main.group(1)).replaceAll("\\.[cm]?jsx?$", "");
      final Optional<String> declared = firstExisting(stem);
      if (declared.isPresent()) {
        return declared;
      }
    }
    return firstExisting(packageDirectory + "/index");
  }

  /** Returns the name of the {@code @types} directory of a package. */
  static String typesName(final String packageName) {
    return packageName.startsWith("@") ? packageName.substring(1).replace("/", "__") : packageName;
  }
}
