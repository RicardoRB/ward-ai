package dev.ward.domain.buildpolicy.maven;

import java.util.ArrayList;
import java.util.List;

/**
 * The default lifecycle plugins of a Maven packaging, with the versions Maven 3.9.16 binds. Init
 * pins them and the {@code plugin-version-pinned} rule requires them, from this one table.
 */
public final class MavenLifecyclePlugins {
  /** The plugins of a {@code jar} build. */
  public static final List<Plugin> JAR =
      List.of(
          new Plugin("maven-clean-plugin", "3.2.0"),
          new Plugin("maven-resources-plugin", "3.4.0"),
          new Plugin("maven-compiler-plugin", "3.15.0"),
          new Plugin("maven-surefire-plugin", "3.5.4"),
          new Plugin("maven-jar-plugin", "3.5.0"),
          new Plugin("maven-install-plugin", "3.1.4"),
          new Plugin("maven-deploy-plugin", "3.1.4"),
          new Plugin("maven-site-plugin", "3.12.1"));

  private static final Plugin WAR = new Plugin("maven-war-plugin", "3.4.0");

  private MavenLifecyclePlugins() {}

  /** A default lifecycle plugin and the version init pins for it. */
  public record Plugin(String artifact, String version) {}

  /** Returns the default lifecycle plugins of {@code packaging}. */
  public static List<Plugin> forPackaging(final String packaging) {
    return switch (packaging) {
      case "jar" -> JAR;
      case "war" -> withWar();
      default -> others();
    };
  }

  /** The plugins of every packaging that has no compile or test phase of its own. */
  private static List<Plugin> others() {
    return JAR.stream()
        .filter(
            plugin ->
                List.of("clean", "install", "deploy", "site").stream()
                    .anyMatch(name -> plugin.artifact().startsWith("maven-" + name)))
        .toList();
  }

  private static List<Plugin> withWar() {
    final List<Plugin> plugins = new ArrayList<>(JAR);
    plugins.removeIf(plugin -> "maven-jar-plugin".equals(plugin.artifact()));
    plugins.add(WAR);
    return List.copyOf(plugins);
  }
}
