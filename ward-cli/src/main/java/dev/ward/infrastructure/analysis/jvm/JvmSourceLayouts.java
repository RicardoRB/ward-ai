package dev.ward.infrastructure.analysis.jvm;

import dev.ward.analysis.domain.jvm.JavaSourceRoot;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the main and test source roots of each Maven or Gradle module from its build file, without
 * running the build. A root the build file does not set as a literal, such as one built from a
 * property, falls back to the default root.
 */
public final class JvmSourceLayouts {
  private static final Logger LOGGER = Logger.getLogger(JvmSourceLayouts.class.getName());
  private static final String MAIN_DEFAULT = "src/main/java";
  private static final String TEST_DEFAULT = "src/test/java";
  private static final String POM = "pom.xml";
  private static final List<String> GRADLE_SCRIPTS = List.of("build.gradle.kts", "build.gradle");
  private static final Pattern MAVEN_BUILD =
      Pattern.compile("<build>(.*?)</build>", Pattern.DOTALL);
  private static final Pattern MAVEN_MAIN =
      Pattern.compile("<sourceDirectory>\\s*([^<$]+?)\\s*</sourceDirectory>");
  private static final Pattern MAVEN_TEST =
      Pattern.compile("<testSourceDirectory>\\s*([^<$]+?)\\s*</testSourceDirectory>");
  private static final Pattern GRADLE_MAIN = sourceSetDirectory("main");
  private static final Pattern GRADLE_TEST = sourceSetDirectory("test");

  private JvmSourceLayouts() {}

  /**
   * Returns the main and test roots of each module directory, relative to the project root. The
   * project root itself is a module with an empty path.
   *
   * @param projectRoot the project root
   * @param moduleDirs the module directories, each with its build file
   * @return the roots of every module, main before test within a module
   */
  public static List<JavaSourceRoot> of(final Path projectRoot, final List<Path> moduleDirs) {
    final List<JavaSourceRoot> roots = new ArrayList<>();
    for (final Path module : moduleDirs) {
      roots.addAll(rootsOf(projectRoot, module));
    }
    LOGGER.fine(
        () ->
            "Java source layout: "
                + moduleDirs.size()
                + " modules, "
                + roots.size()
                + " source roots");
    return List.copyOf(roots);
  }

  private static List<JavaSourceRoot> rootsOf(final Path projectRoot, final Path module) {
    final Directories dirs = directoriesOf(module);
    final String moduleId = relative(projectRoot, module);
    return List.of(
        new JavaSourceRoot(relative(projectRoot, module.resolve(dirs.main())), moduleId, false),
        new JavaSourceRoot(relative(projectRoot, module.resolve(dirs.test())), moduleId, true));
  }

  /** Reads the Maven build section when there is a POM, else the Gradle script, else defaults. */
  private static Directories directoriesOf(final Path module) {
    final Path pom = module.resolve(POM);
    if (Files.isRegularFile(pom)) {
      final String build = firstGroup(read(pom), MAVEN_BUILD).orElse("");
      return new Directories(
          firstGroup(build, MAVEN_MAIN).orElse(MAIN_DEFAULT),
          firstGroup(build, MAVEN_TEST).orElse(TEST_DEFAULT));
    }
    final Optional<Path> script = gradleScript(module);
    if (script.isPresent()) {
      final String text = read(script.get());
      return new Directories(
          firstGroup(text, GRADLE_MAIN).orElse(MAIN_DEFAULT),
          firstGroup(text, GRADLE_TEST).orElse(TEST_DEFAULT));
    }
    return new Directories(MAIN_DEFAULT, TEST_DEFAULT);
  }

  private static Optional<Path> gradleScript(final Path module) {
    return GRADLE_SCRIPTS.stream().map(module::resolve).filter(Files::isRegularFile).findFirst();
  }

  /** Returns the first group of {@code pattern} in {@code text}, if the pattern matches. */
  private static Optional<String> firstGroup(final String text, final Pattern pattern) {
    final Matcher match = pattern.matcher(text);
    return match.find() ? Optional.of(match.group(1)) : Optional.empty();
  }

  /** Matches a literal {@code srcDir} inside the java block of a source set, Groovy or Kotlin. */
  private static Pattern sourceSetDirectory(final String sourceSet) {
    return Pattern.compile(
        sourceSet + "\\s*\\{\\s*java\\s*\\{\\s*srcDirs?\\s*[(=]?\\s*\\[?\\s*['\"]([^'\"$]+)['\"]");
  }

  private static String relative(final Path projectRoot, final Path path) {
    return projectRoot.relativize(path.normalize()).toString().replace('\\', '/');
  }

  private static String read(final Path file) {
    try {
      return Files.readString(file);
    } catch (IOException e) {
      throw new UncheckedIOException("Could not read build file " + file, e);
    }
  }

  /** The source directories of one module, relative to the module. */
  private record Directories(String main, String test) {}
}
