package dev.ward.infrastructure.buildpolicy.maven;

import dev.ward.domain.buildpolicy.BuildEcosystem;
import dev.ward.domain.buildpolicy.BuildModel;
import dev.ward.domain.buildpolicy.BuildModelReader;
import dev.ward.domain.buildpolicy.Declaration;
import dev.ward.domain.buildpolicy.DeclarationKind;
import dev.ward.domain.buildpolicy.ToolVersion;
import dev.ward.domain.buildpolicy.maven.MavenLifecyclePlugins;
import dev.ward.infrastructure.buildpolicy.BuildPaths;
import dev.ward.infrastructure.buildpolicy.WrapperVersions;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Reads {@code pom.xml} and the Maven wrapper properties of a directory. */
public final class MavenBuildReader implements BuildModelReader {
  private static final Logger LOGGER = Logger.getLogger(MavenBuildReader.class.getName());
  private static final String POM = "pom.xml";
  private static final String PLUGINS = "plugins";
  private static final String PLUGIN_GROUP = "org.apache.maven.plugins";
  private static final String DEPENDENCIES = "dependencies";
  private static final String ARTIFACT_ID = "artifactId";

  @Override
  public List<String> modules(final Path root, final String dir) {
    if (!Files.isRegularFile(BuildPaths.file(root, dir, POM))) {
      return List.of();
    }
    final String shown = BuildPaths.relative(dir, POM);
    final PomNode pom = PomParser.parse(BuildPaths.file(root, dir, POM), shown);
    final List<String> modules = new ArrayList<>();
    final List<PomNode> listed = new ArrayList<>(pom.list("modules"));
    pom.list("profiles").forEach(profile -> listed.addAll(profile.list("modules")));
    for (final PomNode module : listed) {
      final String name = module.text().strip();
      final String directory =
          name.endsWith("/" + POM) ? name.substring(0, name.length() - POM.length() - 1) : name;
      modules.add(normalize(dir, directory));
    }
    return modules;
  }

  private static String normalize(final String dir, final String module) {
    return Path.of(dir).resolve(module).normalize().toString().replace('\\', '/');
  }

  @Override
  public BuildModel read(final Path root, final String dir) {
    final List<Declaration> declarations = new ArrayList<>();
    final List<ToolVersion> tools = new ArrayList<>();
    if (Files.isRegularFile(BuildPaths.file(root, dir, POM))) {
      final String shown = BuildPaths.relative(dir, POM);
      LOGGER.log(Level.FINE, "event=build-policy-read-pom file={0}", shown);
      final Collector collector =
          new Collector(PomChain.load(root, BuildPaths.file(root, dir, POM)), shown, declarations);
      collector.plugins();
      collector.lifecycle();
      collector.dependencies();
    }
    final String wrapper = BuildPaths.relative(dir, ".mvn/wrapper/maven-wrapper.properties");
    WrapperVersions.read(root.resolve(wrapper), wrapper, "maven", "apache-maven-")
        .ifPresent(tools::add);
    return new BuildModel(declarations, tools);
  }

  /** Collects the declarations of one POM. */
  private static final class Collector {
    private final PomChain chain;
    private final String file;
    private final List<Declaration> out;

    Collector(final PomChain chain, final String file, final List<Declaration> out) {
      this.chain = chain;
      this.file = file;
      this.out = out;
    }

    void plugins() {
      final PomNode pom = chain.pom();
      plugins("build plugins", pom.list("build", PLUGINS), true);
      plugins("pluginManagement", pom.list("build", "pluginManagement", PLUGINS), false);
      plugins("reporting plugins", pom.list("reporting", PLUGINS), true);
      for (final PomNode profile : pom.list("profiles")) {
        final String id = "profile " + profile.text("id") + " ";
        plugins(id + "plugins", profile.list("build", PLUGINS), true);
        plugins(id + "pluginManagement", profile.list("build", "pluginManagement", PLUGINS), false);
      }
      for (final PomNode extension : pom.list("build", "extensions")) {
        out.add(plugin("extensions", extension, false));
      }
    }

    private void plugins(final String section, final List<PomNode> nodes, final boolean inherit) {
      for (final PomNode node : nodes) {
        out.add(plugin(section, node, inherit));
      }
    }

    private Declaration plugin(final String section, final PomNode node, final boolean inherit) {
      final String artifact = node.text(ARTIFACT_ID);
      final String group = node.text("groupId").isEmpty() ? PLUGIN_GROUP : node.text("groupId");
      String version = node.text("version").isEmpty() ? null : chain.resolve(node.text("version"));
      if (version == null && inherit) {
        version = chain.managed(artifact);
      }
      return pluginDeclaration(section, group + ":" + artifact, version, node.line());
    }

    private Declaration pluginDeclaration(
        final String section, final String name, final String version, final int line) {
      final Declaration.Flag flag =
          version == null && chain.externalParent()
              ? Declaration.Flag.EXTERNAL_PARENT
              : Declaration.Flag.NONE;
      return new Declaration(
          BuildEcosystem.MAVEN,
          DeclarationKind.PLUGIN,
          name,
          version,
          section,
          "",
          file,
          line,
          flag);
    }

    void lifecycle() {
      final PomNode packaging = chain.pom().child("packaging");
      final int line = packaging == null ? chain.pom().line() : packaging.line();
      for (final MavenLifecyclePlugins.Plugin plugin :
          MavenLifecyclePlugins.forPackaging(chain.packaging())) {
        if (!chain.declares(plugin.artifact())) {
          out.add(
              pluginDeclaration(
                  "default lifecycle", PLUGIN_GROUP + ":" + plugin.artifact(), null, line));
        }
      }
    }

    void dependencies() {
      final PomNode pom = chain.pom();
      dependencies(DEPENDENCIES, pom.list(DEPENDENCIES));
      dependencies("dependencyManagement", pom.list("dependencyManagement", DEPENDENCIES));
      for (final PomNode profile : pom.list("profiles")) {
        final String id = "profile " + profile.text("id") + " ";
        dependencies(id + DEPENDENCIES, profile.list(DEPENDENCIES));
        dependencies(
            id + "dependencyManagement", profile.list("dependencyManagement", DEPENDENCIES));
      }
    }

    private void dependencies(final String section, final List<PomNode> nodes) {
      for (final PomNode node : nodes) {
        final String name = node.text("groupId") + ":" + node.text(ARTIFACT_ID);
        final String type = node.text("type").isEmpty() ? "jar" : node.text("type");
        final PomNode version = node.child("version");
        out.add(
            new Declaration(
                BuildEcosystem.MAVEN,
                DeclarationKind.DEPENDENCY,
                name,
                version == null ? null : chain.resolve(version.text()),
                section,
                name + ":" + type + ":" + node.text("classifier"),
                file,
                version == null ? node.line() : version.line(),
                Declaration.Flag.NONE));
      }
    }
  }
}
