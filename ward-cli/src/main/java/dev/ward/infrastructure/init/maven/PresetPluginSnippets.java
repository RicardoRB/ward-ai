package dev.ward.infrastructure.init.maven;

import dev.ward.domain.buildpolicy.maven.MavenLifecyclePlugins;
import dev.ward.domain.init.QualityPreset;
import java.util.List;
import java.util.Set;

/** Plugin declarations tuned by a quality preset, used instead of the default declarations. */
final class PresetPluginSnippets {
  /**
   * Default lifecycle plugins with the versions Maven 3.9.16 binds, so pinning them changes no
   * build behavior while satisfying {@code ward:build/plugin-version-pinned}. The table is the one
   * the rule reads.
   */
  static final List<LifecyclePlugin> LIFECYCLE_PLUGINS =
      MavenLifecyclePlugins.JAR.stream()
          .map(plugin -> new LifecyclePlugin(plugin.artifact(), plugin.version()))
          .toList();

  private PresetPluginSnippets() {}

  static String pit(final QualityPreset preset) {
    if (preset.rules().mutationThreshold() == null) {
      return PomTools.PIT_PLUGIN;
    }
    return """
            <plugin>
              <groupId>org.pitest</groupId>
              <artifactId>pitest-maven</artifactId>
              <version>1.30.1</version>
              <dependencies>
                <dependency>
                  <groupId>org.pitest</groupId>
                  <artifactId>pitest-junit5-plugin</artifactId>
                  <version>1.2.3</version>
                </dependency>
              </dependencies>
              <configuration>
                <mutationThreshold>THRESHOLD</mutationThreshold>
              </configuration>
            </plugin>
          """
        .replace("THRESHOLD", String.valueOf(preset.rules().mutationThreshold()));
  }

  /**
   * Pins each unpinned lifecycle plugin when the strict preset runs the {@code build-policy} gate,
   * which requires plugin versions, recording one plan detail per pin.
   */
  static String pinLifecycle(
      final String pom,
      final Set<String> gates,
      final QualityPreset preset,
      final List<String> details) {
    if (preset != QualityPreset.STRICT || !gates.contains("build-policy")) {
      return pom;
    }
    String pinned = pom;
    for (final LifecyclePlugin plugin : LIFECYCLE_PLUGINS) {
      if (!pinned.contains("<artifactId>" + plugin.artifact() + "</artifactId>")) {
        pinned = PomXml.addManagedPlugin(pinned, plugin.snippet());
        details.add("+ " + plugin.artifact() + " " + plugin.version() + " (pluginManagement)");
      }
    }
    return pinned;
  }

  /** A default lifecycle plugin and the version init pins for it. */
  record LifecyclePlugin(String artifact, String version) {
    String snippet() {
      return "<plugin>\n  <groupId>org.apache.maven.plugins</groupId>\n  <artifactId>"
          + artifact
          + "</artifactId>\n  <version>"
          + version
          + "</version>\n</plugin>\n";
    }
  }
}
