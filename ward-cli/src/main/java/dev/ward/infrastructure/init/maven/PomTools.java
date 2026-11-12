package dev.ward.infrastructure.init.maven;

import dev.ward.domain.init.QualityPreset;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;

/** The Maven tools init can add to pom.xml, one row per gate, with their default declarations. */
final class PomTools {
  private static final String SONAR = "sonar-maven-plugin";
  private static final String JACOCO = "jacoco-maven-plugin";
  private static final String DEPENDENCY_CHECK = "dependency-check-maven";
  private static final String PIT = "pitest-maven";
  private static final String ENFORCER = "maven-enforcer-plugin";
  private static final String JAPICMP = "japicmp-maven-plugin";

  private static final String JAPICMP_PLUGIN =
      """
              <plugin>
                <groupId>com.github.siom79.japicmp</groupId>
                <artifactId>japicmp-maven-plugin</artifactId>
                <version>0.23.1</version>
                <configuration>
                  <oldVersion>
                    <dependency>
                      <groupId>${project.groupId}</groupId>
                      <artifactId>${project.artifactId}</artifactId>
                      <version>RELEASE</version>
                      <type>jar</type>
                    </dependency>
                  </oldVersion>
                  <newVersion><file><path>${project.build.directory}/${project.build.finalName}.jar</path></file></newVersion>
                  <parameter>
                    <breakBuildOnBinaryIncompatibleModifications>true</breakBuildOnBinaryIncompatibleModifications>
                    <includes><include>${project.groupId}.*</include></includes>
                  </parameter>
                </configuration>
              </plugin>
            """;

  private static final String SONAR_PLUGIN =
      """
              <plugin>
                <groupId>org.sonarsource.scanner.maven</groupId>
                <artifactId>sonar-maven-plugin</artifactId>
                <version>5.1.0.4751</version>
              </plugin>
            """;
  private static final String JACOCO_PLUGIN =
      """
              <plugin>
                <groupId>org.jacoco</groupId>
                <artifactId>jacoco-maven-plugin</artifactId>
                <version>0.8.13</version>
                <configuration>
                  <rules>
                    <rule>
                      <element>BUNDLE</element>
                      <limits>
                        <limit>
                          <counter>LINE</counter>
                          <value>COVEREDRATIO</value>
                          <minimum>0.80</minimum>
                        </limit>
                      </limits>
                    </rule>
                  </rules>
                </configuration>
              </plugin>
            """;
  private static final String DEPENDENCY_CHECK_PLUGIN =
      """
              <plugin>
                <groupId>org.owasp</groupId>
                <artifactId>dependency-check-maven</artifactId>
                <version>12.1.0</version>
                 <configuration>
                   <failBuildOnCVSS>7</failBuildOnCVSS>
                   <nvdApiKeyEnvironmentVariable>NVD_API_KEY</nvdApiKeyEnvironmentVariable>
                   <ossindexAnalyzerEnabled>false</ossindexAnalyzerEnabled>
                   <ossIndexServerId>ossindex</ossIndexServerId>
                 </configuration>
              </plugin>
            """;
  static final String PIT_PLUGIN =
      """
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
              </plugin>
            """;
  private static final String ENFORCER_PLUGIN =
      """
              <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-enforcer-plugin</artifactId>
                <version>3.5.0</version>
                <configuration>
                  <rules>
                    <dependencyConvergence/>
                  </rules>
                  <fail>true</fail>
                </configuration>
              </plugin>
            """;

  /** Tools in the order they are added to pom.xml. */
  static final List<Tool> TOOLS =
      List.of(
          Tool.plugin("sonar", SONAR, SONAR_PLUGIN),
          Tool.modulePlugin("coverage", JACOCO, JACOCO_PLUGIN, PresetPluginSnippets::jacoco),
          Tool.plugin("dependency-check", DEPENDENCY_CHECK, DEPENDENCY_CHECK_PLUGIN),
          Tool.modulePlugin("mutation", PIT, PIT_PLUGIN, PresetPluginSnippets::pit),
          Tool.modulePlugin(
              "enforcer", ENFORCER, ENFORCER_PLUGIN, preset -> PresetPluginSnippets.enforcer()),
          Tool.plugin("api-compat", JAPICMP, JAPICMP_PLUGIN));

  private PomTools() {}

  /** Returns whether {@code pom} declares {@code artifactId}. */
  static boolean declares(final String pom, final String artifactId) {
    return pom.contains("<artifactId>" + artifactId + "</artifactId>");
  }

  /** Declarations for the selected tools that a run added to the pom. */
  static List<PomDeclarationValidator.Declaration> addedDeclarations(
      final String original, final String updated, final Set<String> selected) {
    return TOOLS.stream()
        .filter(tool -> selected.contains(tool.gate()))
        .filter(tool -> !declares(original, tool.artifact()))
        .filter(tool -> declares(updated, tool.artifact()))
        .map(tool -> new PomDeclarationValidator.Declaration(tool.artifact(), tool.dependency()))
        .toList();
  }

  /**
   * A Maven tool added for a selected gate. Its preset snippet, when present, replaces the default
   * snippet under a quality preset. A module-placed tool goes into each Java module of a
   * multi-module build, and the aggregator pins its version in {@code pluginManagement}.
   */
  record Tool(
      String gate,
      String artifact,
      String snippet,
      Function<QualityPreset, String> presetSnippet,
      boolean dependency,
      boolean module) {
    private static final String VERSION_START = "<version>";
    private static final String GROUP_START = "<groupId>";

    static Tool plugin(final String gate, final String artifact, final String snippet) {
      return new Tool(gate, artifact, snippet, null, false, false);
    }

    static Tool plugin(
        final String gate,
        final String artifact,
        final String snippet,
        final Function<QualityPreset, String> presetSnippet) {
      return new Tool(gate, artifact, snippet, presetSnippet, false, false);
    }

    /** A plugin placed in each Java module of a multi-module build, not in the aggregator. */
    static Tool modulePlugin(final String gate, final String artifact, final String snippet) {
      return new Tool(gate, artifact, snippet, null, false, true);
    }

    static Tool modulePlugin(
        final String gate,
        final String artifact,
        final String snippet,
        final Function<QualityPreset, String> presetSnippet) {
      return new Tool(gate, artifact, snippet, presetSnippet, false, true);
    }

    /** The plugin or dependency declaration, with the chosen versions filled in. */
    String declaration(final QualityPreset preset, final String selectedVersion) {
      final String chosen = chosen(preset);
      return selectedVersion == null
          ? chosen
          : chosen.replaceFirst(
              "<version>[^<]+</version>",
              Matcher.quoteReplacement("<version>" + selectedVersion + "</version>"));
    }

    String addTo(final String pom, final QualityPreset preset, final String selectedVersion) {
      final String declaration = declaration(preset, selectedVersion);
      return dependency
          ? PomXml.addDependency(pom, declaration)
          : PomXml.addPlugin(pom, declaration);
    }

    /** The aggregator's {@code pluginManagement} entry: coordinates and the chosen version only. */
    String managed(final QualityPreset preset, final String selectedVersion) {
      final String chosen = chosen(preset);
      final int start = chosen.indexOf(GROUP_START) + GROUP_START.length();
      final String group = chosen.substring(start, chosen.indexOf("</groupId>", start));
      return "<plugin>\n  <groupId>"
          + group
          + "</groupId>\n  <artifactId>"
          + artifact
          + "</artifactId>\n  <version>"
          + version(preset, selectedVersion)
          + "</version>\n</plugin>\n";
    }

    private String chosen(final QualityPreset preset) {
      return preset == null || presetSnippet == null ? snippet : presetSnippet.apply(preset);
    }

    /** The version this tool's declaration pins, preferring a compatibility-selected version. */
    String version(final QualityPreset preset, final String selectedVersion) {
      if (selectedVersion != null) {
        return selectedVersion;
      }
      final String chosen = chosen(preset);
      final int start = chosen.indexOf(VERSION_START);
      return start < 0
          ? ""
          : chosen.substring(start + VERSION_START.length(), chosen.indexOf("</version>", start));
    }
  }
}
