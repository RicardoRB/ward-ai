package dev.ward.domain.project;

import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * A detected project.
 *
 * @param root the project root directory
 * @param language its primary language
 * @param buildTool its build tool
 * @param framework its framework
 * @param mavenWrapperAvailable whether the build tool's wrapper, such as {@code mvnw}, is present;
 *     for pip, whether the project root has a {@code .venv} that the build runs through
 * @param publishesArtifacts whether the Maven POM declares {@code <distributionManagement>}, the
 *     signal that a released library baseline exists
 * @param repositoryRoot the Git top-level containing the project, or {@code root} outside a Git
 *     work tree
 * @param languages every source language the project contains; always includes {@code language}
 * @param languageVersion the target language major version, such as the Node version, or empty when
 *     none is detected
 * @param taskRunner the tool that runs the workspace's per-package tasks, {@link TaskRunner#NONE}
 *     unless a workspace root declares one
 */
public record Project(
    Path root,
    Language language,
    BuildTool buildTool,
    Framework framework,
    boolean mavenWrapperAvailable,
    boolean publishesArtifacts,
    Path repositoryRoot,
    Set<Language> languages,
    String languageVersion,
    TaskRunner taskRunner) {
  /** Copies {@code languages}, in declaration order, and adds the primary language. */
  public Project {
    final Set<Language> all = EnumSet.of(language);
    all.addAll(languages);
    languages = Collections.unmodifiableSet(all);
  }

  /** Creates a project without a Maven Wrapper. */
  public Project(
      final Path root,
      final Language language,
      final BuildTool buildTool,
      final Framework framework) {
    this(root, language, buildTool, framework, false);
  }

  /** Creates a project that does not publish artifacts. */
  public Project(
      final Path root,
      final Language language,
      final BuildTool buildTool,
      final Framework framework,
      final boolean mavenWrapperAvailable) {
    this(
        root,
        language,
        buildTool,
        framework,
        mavenWrapperAvailable,
        false,
        root,
        Set.of(),
        "",
        TaskRunner.NONE);
  }

  /** Returns this project with {@code runner} as its task runner. */
  public Project withTaskRunner(final TaskRunner runner) {
    return new Project(
        root,
        language,
        buildTool,
        framework,
        mavenWrapperAvailable,
        publishesArtifacts,
        repositoryRoot,
        languages,
        languageVersion,
        runner);
  }

  /** Returns this project inside the Git work tree whose top-level is {@code repository}. */
  public Project withRepositoryRoot(final Path repository) {
    return new Project(
        root,
        language,
        buildTool,
        framework,
        mavenWrapperAvailable,
        publishesArtifacts,
        repository,
        languages,
        languageVersion,
        taskRunner);
  }

  /**
   * Returns the project root relative to the repository root with {@code /} separators, or an empty
   * string when the project root is the repository root.
   */
  public String projectPath() {
    return repositoryRoot.relativize(root).toString().replace('\\', '/');
  }

  /**
   * Returns the project path as a file-name fragment: lower case, with every run of characters
   * other than letters and digits replaced by {@code -}; empty for a root project.
   */
  public String pathSlug() {
    return slug(projectPath());
  }

  /** Returns {@code path} as a file-name fragment, as {@link #pathSlug()} does. */
  public static String slug(final String path) {
    return path.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
  }

  /**
   * Returns where a tool that {@code atRepositoryRoot} installs its files for this project: the
   * repository root for a nested project, else the project root.
   */
  public Path installRoot(final boolean atRepositoryRoot) {
    return atRepositoryRoot && nested() ? repositoryRoot : root;
  }

  /** Returns the project path a tool installed at {@link #installRoot} needs, or empty. */
  public String installPath(final boolean atRepositoryRoot) {
    return atRepositoryRoot && nested() ? projectPath() : "";
  }

  /** Returns whether the project sits below the repository root rather than at it. */
  public boolean nested() {
    return !projectPath().isEmpty();
  }

  /**
   * Returns whether an API-compatibility baseline can exist: Maven needs a released artifact, which
   * only projects declaring {@code <distributionManagement>} publish; Gradle configures its own.
   */
  public boolean hasApiBaseline() {
    return buildTool != BuildTool.MAVEN || publishesArtifacts;
  }

  /** Returns the toolchain of this project's build tool. */
  public Toolchain toolchain() {
    return buildTool.toolchain();
  }

  /** Returns whether the wrapper belonging to this project's build tool is available. */
  public boolean wrapperAvailable() {
    return mavenWrapperAvailable;
  }

  /** Returns the wrapper-first executable name for this project's build tool. */
  public String buildExecutable() {
    return switch (buildTool) {
      case MAVEN -> wrapperOr("./mvnw", "mvn");
      case GRADLE -> wrapperOr("./gradlew", "gradle");
      case NPM -> "npm";
      case PNPM -> "pnpm";
      case YARN -> "yarn";
      case BUN -> "bun";
      case UV -> "uv";
      case POETRY -> "poetry";
      default -> buildTool.displayName();
    };
  }

  /** Returns the wrapper when this project has one, else the executable on PATH. */
  private String wrapperOr(final String wrapper, final String executable) {
    return mavenWrapperAvailable ? wrapper : executable;
  }
}
