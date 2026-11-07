package dev.ward.infrastructure.init;

import dev.ward.domain.config.WardException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** The base package of a Java project, which its architecture tables name by source root. */
final class JavaBasePackage {
  private static final Pattern PACKAGE = Pattern.compile("\\bpackage\\s+([A-Za-z_][\\w.]*)\\s*;");
  private static final String DEFAULT_PACKAGE = "com.example";
  private static final Set<String> LAYERS =
      Set.of(
          "domain",
          "application",
          "infrastructure",
          "adapter",
          "adapters",
          "core",
          "controller",
          "service",
          "repository",
          "model",
          "api",
          "web",
          "persistence");

  private JavaBasePackage() {}

  /**
   * The deepest package shared by every main source, so the tables cover the whole application
   * rather than the package of whichever file is listed first. A prefix that ends in a layer name
   * means only that layer exists yet, so its parent is used.
   */
  static String of(final Path root) {
    List<String> common = null;
    for (final List<String> sourcePackage : sourcePackages(root.resolve("src/main/java"))) {
      common = commonPrefix(common, sourcePackage);
    }
    if (common != null && common.size() > 1 && LAYERS.contains(common.getLast())) {
      common = common.subList(0, common.size() - 1);
    }
    return common == null || common.isEmpty() ? DEFAULT_PACKAGE : String.join(".", common);
  }

  /** The declared package of each main source, as name segments. */
  private static List<List<String>> sourcePackages(final Path source) {
    if (!Files.isDirectory(source)) {
      return List.of();
    }
    final List<List<String>> packages = new ArrayList<>();
    try (Stream<Path> files = Files.walk(source)) {
      for (final Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
        final Matcher matcher = PACKAGE.matcher(Files.readString(file));
        if (matcher.find()) {
          packages.add(List.of(matcher.group(1).split("\\.")));
        }
      }
    } catch (IOException e) {
      throw new WardException("Could not inspect Java packages: " + e.getMessage(), e);
    }
    return packages;
  }

  private static List<String> commonPrefix(final List<String> current, final List<String> next) {
    if (current == null) {
      return next;
    }
    int shared = 0;
    while (shared < Math.min(current.size(), next.size())
        && current.get(shared).equals(next.get(shared))) {
      shared++;
    }
    return current.subList(0, shared);
  }
}
