package dev.ward.infrastructure.doctor;

import dev.ward.domain.doctor.EnvironmentFacts;
import dev.ward.domain.doctor.EnvironmentInspection;
import dev.ward.domain.doctor.ExecutableObservation;
import dev.ward.domain.doctor.OssIndexSetting;
import dev.ward.domain.process.CommandExecutor;
import dev.ward.domain.process.CommandResult;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

/** Reads executable availability and prerequisite markers from the current machine. */
public final class SystemEnvironmentInspection implements EnvironmentInspection {
  private static final Logger LOGGER =
      Logger.getLogger(SystemEnvironmentInspection.class.getName());
  private static final Set<String> PROBED_EXECUTABLES =
      Set.of(
          "mvn",
          "gradle",
          "java",
          "node",
          "npm",
          "pnpm",
          "yarn",
          "bun",
          "git",
          "claude",
          "opencode",
          "brew",
          "pipx",
          "python",
          "python3",
          "uv",
          "poetry",
          "trivy",
          "gitleaks",
          "semgrep",
          "zap-baseline.py",
          "vacuum",
          "oasdiff");
  // The files a FastAPI project keeps its application in, the first match wins.
  private static final List<Path> FASTAPI_FILES =
      List.of(
          Path.of("main.py"),
          Path.of("app.py"),
          Path.of("app", "main.py"),
          Path.of("app", "app.py"));
  private static final Pattern FASTAPI_ASSIGNMENT =
      Pattern.compile("(?m)^(\\w+)\\s*=\\s*FastAPI\\(");
  private static final List<String> OPENAPI_SPECS =
      List.of(
          "src/main/resources/openapi.yml",
          "src/main/resources/openapi.yaml",
          "src/main/resources/openapi.json",
          "openapi.yml",
          "openapi.yaml",
          "openapi.json");

  private final CommandExecutor executor;

  /** Creates the adapter; {@code executor} runs Git to check ignore rules. */
  @Inject
  public SystemEnvironmentInspection(final CommandExecutor executor) {
    this.executor = executor;
  }

  @Override
  public EnvironmentFacts inspect(final Path projectRoot) {
    final Set<String> available = new HashSet<>();
    final String path = System.getenv("PATH");
    if (path != null) {
      Arrays.stream(path.split(File.pathSeparator))
          .map(Path::of)
          .forEach(
              directory ->
                  PROBED_EXECUTABLES.stream()
                      .filter(name -> Files.isExecutable(directory.resolve(name)))
                      .forEach(available::add));
    }
    final Map<String, String> values =
        Map.of(
            "mavenWrapperAvailable",
            Boolean.toString(Files.isExecutable(projectRoot.resolve("mvnw"))),
            "gradleWrapperAvailable",
            Boolean.toString(Files.isExecutable(projectRoot.resolve("gradlew"))),
            "configurationPresent",
            Boolean.toString(Files.isRegularFile(projectRoot.resolve("ward.toml"))),
            "openCodeCommandPresent",
            Boolean.toString(
                Files.isRegularFile(projectRoot.resolve(".opencode/commands/ward-check.md"))),
            "nativeExecutablePresent",
            Boolean.toString(Files.isExecutable(Path.of("target/ward"))));
    final Map<String, String> observed = new HashMap<>(values);
    observed.put("NVD_API_KEY", Boolean.toString(isSet("NVD_API_KEY")));
    observed.put("OSS_INDEX_PASSWORD", Boolean.toString(isSet("OSS_INDEX_PASSWORD")));
    observed.put("OASDIFF_CONFIG", Boolean.toString(isSet("OASDIFF_CONFIG")));
    LOGGER.log(
        Level.INFO,
        () -> "event=environment-inspection status=complete available=" + available.size());
    return new EnvironmentFacts(available, observed);
  }

  @Override
  public ExecutableObservation inspectExecutable(final Path projectRoot, final String executable) {
    final Path candidate;
    if (executable.contains("/") || executable.contains(File.separator)) {
      candidate =
          Path.of(executable).isAbsolute()
              ? Path.of(executable)
              : projectRoot.resolve(executable).normalize();
    } else {
      candidate = findOnPath(executable);
    }
    if (candidate == null) {
      return new ExecutableObservation(executable, null, false, false, false);
    }
    return new ExecutableObservation(
        executable,
        candidate,
        Files.exists(candidate),
        Files.isRegularFile(candidate),
        Files.isExecutable(candidate));
  }

  @Override
  public boolean hasOpenSpecDirectory(final Path projectRoot) {
    return Files.isDirectory(projectRoot.resolve("openspec"));
  }

  /** Asks Git, so nested ignore files, global excludes, and negations all apply. */
  @Override
  public boolean isOpenSpecDirectoryIgnored(final Path projectRoot) {
    final CommandResult result =
        executor.execute(List.of("git", "check-ignore", "-q", "openspec"), projectRoot);
    return result.executionError() == null && result.exitCode() == 0;
  }

  /** Asks Git which candidates are tracked, so an untracked draft spec is never offered. */
  @Override
  public Optional<String> openApiSpec(final Path projectRoot) {
    final List<String> command = new ArrayList<>(List.of("git", "ls-files", "-z", "--"));
    command.addAll(OPENAPI_SPECS);
    final CommandResult result = executor.execute(command, projectRoot);
    if (result.executionError() != null || result.exitCode() != 0) {
      return Optional.empty();
    }
    final List<String> tracked = Arrays.asList(result.stdout().split("\\u0000"));
    return OPENAPI_SPECS.stream().filter(tracked::contains).findFirst();
  }

  @Override
  public Optional<String> fastApiApp(final Path projectRoot) {
    return FASTAPI_FILES.stream()
        .map(projectRoot::resolve)
        .filter(Files::isRegularFile)
        .flatMap(file -> fastApiAttribute(projectRoot, file).stream())
        .findFirst();
  }

  /** Returns {@code module:attribute} when {@code file} assigns a {@code FastAPI(} instance. */
  private static Optional<String> fastApiAttribute(final Path projectRoot, final Path file) {
    try {
      final Matcher app = FASTAPI_ASSIGNMENT.matcher(Files.readString(file));
      if (!app.find()) {
        return Optional.empty();
      }
      final String module =
          projectRoot.relativize(file).toString().replaceAll("\\.py$", "").replace('/', '.');
      return Optional.of(module + ":" + app.group(1));
    } catch (IOException e) {
      return Optional.empty();
    }
  }

  @Override
  public OssIndexSetting ossIndexSetting(final Path projectRoot) {
    return DependencyCheckSettings.read(projectRoot);
  }

  /** Only presence is recorded; values never leave this method. */
  private static boolean isSet(final String name) {
    final String value = System.getenv(name);
    return value != null && !value.isBlank();
  }

  private static Path findOnPath(final String executable) {
    final String path = System.getenv("PATH");
    if (path == null) {
      return null;
    }
    return Arrays.stream(path.split(File.pathSeparator))
        .map(Path::of)
        .map(directory -> directory.resolve(executable))
        .filter(Files::exists)
        .findFirst()
        .orElse(null);
  }
}
