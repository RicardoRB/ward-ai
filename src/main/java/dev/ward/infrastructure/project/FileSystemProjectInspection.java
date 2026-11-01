package dev.ward.infrastructure.project;

import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.DiscoveredProject;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.ProjectInspection;
import dev.ward.domain.project.TaskRunner;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/** Filesystem-backed project marker scanning and secure Maven metadata inspection. */
public final class FileSystemProjectInspection implements ProjectInspection {
  // Build files are searched this many directories below the repository root.
  static final int MAX_SEARCH_DEPTH = 4;
  public static final String POM = "pom.xml";
  static final String GIT = ".git";

  /** Framework markers in precedence order; the first framework whose group matches wins. */
  private static final List<FrameworkGroup> FRAMEWORK_GROUPS =
      List.of(
          new FrameworkGroup(Framework.SPRING_BOOT, "org.springframework.boot"),
          new FrameworkGroup(Framework.QUARKUS, "io.quarkus"),
          new FrameworkGroup(Framework.MICRONAUT, "io.micronaut"),
          new FrameworkGroup(Framework.HELIDON, "io.helidon"),
          new FrameworkGroup(Framework.DROPWIZARD, "io.dropwizard"),
          new FrameworkGroup(Framework.JAVALIN, "io.javalin"),
          new FrameworkGroup(Framework.KTOR, "io.ktor"));

  private static final List<String> GRADLE_BUILD_FILES =
      List.of("build.gradle", "build.gradle.kts");
  private static final String DISTRIBUTION_MANAGEMENT = "distributionManagement";

  /** Build-file markers in precedence order; the first match decides a directory's project. */
  private static final List<Marker> MARKERS =
      List.of(
          new Marker(POM, FileSystemProjectInspection::mavenProject),
          new Marker("build.gradle.kts", FileSystemProjectInspection::gradleProject),
          new Marker("build.gradle", FileSystemProjectInspection::gradleProject),
          new Marker(NodeProjectInspection.TSCONFIG, NodeProjectInspection::project),
          new Marker(NodeProjectInspection.PACKAGE_JSON, NodeProjectInspection::project),
          new Marker(PythonProjectInspection.PYPROJECT, PythonProjectInspection::project),
          new Marker(PythonProjectInspection.SETUP_PY, PythonProjectInspection::project),
          new Marker(PythonProjectInspection.SETUP_CFG, PythonProjectInspection::project),
          new Marker("go.mod", dir -> new Project(dir, Language.GO, BuildTool.GO, Framework.NONE)),
          new Marker(
              "Cargo.toml",
              dir -> new Project(dir, Language.RUST, BuildTool.CARGO, Framework.NONE)));

  /** Creates the stateless adapter that Dagger injects. */
  @Inject
  public FileSystemProjectInspection() {}

  @Override
  public Optional<Project> detect(final Path start) {
    for (Path dir = start.toAbsolutePath().normalize(); dir != null; dir = dir.getParent()) {
      final Optional<Project> project = projectAt(dir);
      if (project.isPresent()) {
        return Optional.of(ProjectRoots.inRepository(ProjectRoots.aggregatorOf(project.get())));
      }
    }
    return Optional.empty();
  }

  @Override
  public List<DiscoveredProject> discover(final Path start) {
    Path root = start.toAbsolutePath().normalize();
    Path candidate = root;
    Path parent = candidate.getParent();
    while (parent != null && !isRepositoryRoot(candidate)) {
      candidate = parent;
      parent = candidate.getParent();
    }
    if (isRepositoryRoot(candidate)) {
      root = candidate;
    }
    return RepositoryProjectScan.scan(root, MAX_SEARCH_DEPTH).stream()
        .map(
            found ->
                new DiscoveredProject(
                    ProjectRoots.inRepository(found.project()), found.moduleCount()))
        .toList();
  }

  private static boolean isRepositoryRoot(final Path dir) {
    return Files.exists(dir.resolve(GIT)) || Files.exists(dir.resolve(POM));
  }

  static Optional<Project> projectAt(final Path dir) {
    for (final Marker marker : MARKERS) {
      if (Files.isRegularFile(dir.resolve(marker.file()))) {
        return Optional.of(OwnLanguageMembers.ownLanguage(dir, marker.project().apply(dir)));
      }
    }
    try (Stream<Path> files = Files.list(dir)) {
      final List<String> names =
          files.map(Path::getFileName).filter(Objects::nonNull).map(Path::toString).toList();
      if (names.stream().anyMatch(PythonProjectInspection::isRequirementsFile)) {
        return Optional.of(PythonProjectInspection.project(dir));
      }
      final Optional<Project> swift = SwiftProjectInspection.project(dir, names);
      if (swift.isPresent()) {
        return swift;
      }
      return names.stream().anyMatch(name -> name.endsWith(".csproj"))
          ? Optional.of(new Project(dir, Language.CSHARP, BuildTool.DOTNET, Framework.NONE))
          : Optional.empty();
    } catch (IOException e) {
      throw new IllegalStateException("Could not inspect project directory " + dir, e);
    }
  }

  private static Project mavenProject(final Path dir) {
    final Optional<Document> pom = parse(dir.resolve(POM));
    final Set<Language> languages = JvmSourceLanguages.of(dir);
    return new Project(
        dir,
        JvmSourceLanguages.primary(languages),
        BuildTool.MAVEN,
        pom.map(FileSystemProjectInspection::framework).orElse(Framework.NONE),
        Files.isRegularFile(dir.resolve("mvnw")),
        pom.map(FileSystemProjectInspection::publishes).orElse(false),
        dir,
        languages,
        "",
        TaskRunner.NONE,
        null);
  }

  private static Project gradleProject(final Path dir) {
    final Set<Language> languages = JvmSourceLanguages.of(dir);
    return new Project(
        dir,
        JvmSourceLanguages.primary(languages),
        BuildTool.GRADLE,
        gradleFramework(dir),
        Files.isExecutable(dir.resolve("gradlew")),
        false,
        dir,
        languages,
        "",
        TaskRunner.NONE,
        null);
  }

  /** Parses the POM securely; a malformed or hostile POM is still a Maven marker. */
  static Optional<Document> parse(final Path pom) {
    try {
      final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setNamespaceAware(false);
      final DocumentBuilder builder = factory.newDocumentBuilder();
      builder.setErrorHandler(null);
      return Optional.of(builder.parse(pom.toFile()));
    } catch (javax.xml.parsers.ParserConfigurationException | SAXException | IOException e) {
      return Optional.empty();
    }
  }

  /** A framework is detected when a POM groupId is its group or one of its sub-groups. */
  private static Framework framework(final Document pom) {
    final NodeList groupIds = pom.getElementsByTagName("groupId");
    for (final FrameworkGroup group : FRAMEWORK_GROUPS) {
      for (int i = 0; i < groupIds.getLength(); i++) {
        final String groupId = groupIds.item(i).getTextContent().trim();
        if (groupId.equals(group.groupId()) || groupId.startsWith(group.groupId() + ".")) {
          return group.framework();
        }
      }
    }
    return Framework.NONE;
  }

  /**
   * A framework is detected when a Gradle build script mentions its plugin or dependency group. The
   * scripts are read as text, so Groovy and Kotlin DSL both match. The Android Gradle Plugin in the
   * root or in a module wins over the groups, since an Android app may depend on a server library.
   */
  private static Framework gradleFramework(final Path dir) {
    final String script =
        GRADLE_BUILD_FILES.stream().map(f -> readText(dir.resolve(f))).reduce("", String::concat);
    if (GradleAndroidModules.appliesAndroid(script) || !GradleAndroidModules.of(dir).isEmpty()) {
      return Framework.ANDROID;
    }
    for (final FrameworkGroup group : FRAMEWORK_GROUPS) {
      if (script.contains(group.groupId())) {
        return group.framework();
      }
    }
    return Framework.NONE;
  }

  /** Returns the file's text, or an empty string when the file is missing or unreadable. */
  private static String readText(final Path file) {
    if (!Files.isRegularFile(file)) {
      return "";
    }
    try {
      return Files.readString(file);
    } catch (IOException e) {
      return "";
    }
  }

  /** Only a direct child of {@code <project>} counts; profiles do not publish by default. */
  private static boolean publishes(final Document pom) {
    final NodeList children = pom.getDocumentElement().getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      if (DISTRIBUTION_MANAGEMENT.equals(children.item(i).getNodeName())) {
        return true;
      }
    }
    return false;
  }

  private record Marker(String file, Function<Path, Project> project) {}

  private record FrameworkGroup(Framework framework, String groupId) {}
}
