package dev.ward.infrastructure.buildpolicy.dotnet;

import dev.ward.domain.buildpolicy.BuildEcosystem;
import dev.ward.domain.buildpolicy.BuildModel;
import dev.ward.domain.buildpolicy.BuildModelReader;
import dev.ward.domain.buildpolicy.Declaration;
import dev.ward.domain.buildpolicy.DeclarationKind;
import dev.ward.infrastructure.buildpolicy.BuildPaths;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Reads the NuGet package declarations of {@code .csproj}, {@code .fsproj} and {@code .vbproj}
 * files and of {@code Directory.Packages.props} and {@code Directory.Build.props}. The files are
 * read lexically: a {@code PackageReference} or {@code PackageVersion} with a literal version is a
 * declaration, and a version that uses an MSBuild property is marked undeterminable.
 */
public final class DotnetBuildReader implements BuildModelReader {
  private static final Logger LOGGER = Logger.getLogger(DotnetBuildReader.class.getName());
  private static final List<String> SHARED = List.of(
      "Directory.Packages.props",
      "Directory.Build.props"
  );
  private static final List<String> PROJECT_SUFFIXES = List.of(".csproj", ".fsproj", ".vbproj");
  private static final List<String> SOLUTION_SUFFIXES = List.of(".sln", ".slnx");
  private static final String LOCKFILE = "packages.lock.json";
  private static final Pattern COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
  private static final Pattern ELEMENT =
      Pattern.compile(
          "<(PackageReference|PackageVersion)\\b([^>]*?)(?:/>|>(.*?)</\\1\\s*>)",
          Pattern.DOTALL
      );
  private static final Pattern PROJECT_PATH =
      Pattern.compile("[\"']([^\"']+\\.(?:cs|fs|vb)proj)[\"']");
  private static final Pattern CHILD_VERSION =
      Pattern.compile("<Version>\\s*([^<]*?)\\s*</Version>");

  @Override
  public List<String> modules(final Path root, final String dir) {
    final List<String> members = new ArrayList<>();
    for (final Path solution : files(root, dir, SOLUTION_SUFFIXES)) {
      final String text =
          BuildPaths.read(solution, BuildPaths.relative(dir, solution.getFileName().toString()));
      final Matcher matcher = PROJECT_PATH.matcher(text);
      while (matcher.find()) {
        member(root, dir, matcher.group(1).replace('\\', '/')).ifPresent(members::add);
      }
    }
    return members.stream().distinct().toList();
  }

  @Override
  public BuildModel read(final Path root, final String dir) {
    final List<Declaration> declarations = new ArrayList<>();
    final boolean locked = Files.isRegularFile(BuildPaths.file(root, dir, LOCKFILE));
    final List<Path> files = new ArrayList<>(files(root, dir, PROJECT_SUFFIXES));
    SHARED.stream()
        .map(name -> BuildPaths.file(root, dir, name))
        .filter(Files::isRegularFile)
        .forEach(files::add);
    for (final Path file : files) {
      final String shown = BuildPaths.relative(dir, file.getFileName().toString());
      LOGGER.log(Level.FINE, "event=build-policy-read-dotnet file={0}", shown);
      declarations.addAll(declarations(BuildPaths.read(file, shown), shown, locked));
    }
    return new BuildModel(declarations, List.of());
  }

  private static List<Declaration> declarations(
      final String raw,
      final String file,
      final boolean locked
  ) {
    final String text = blankComments(raw);
    final List<Declaration> found = new ArrayList<>();
    final Matcher matcher = ELEMENT.matcher(text);
    while (matcher.find()) {
      final String attributes = matcher.group(2);
      final String name = attribute(attributes, "Include");
      if (name == null) {
        continue;
      }
      final String version = version(attributes, matcher.group(3));
      final String condition = String.valueOf(attribute(attributes, "Condition"));
      found.add(
          new Declaration(
              BuildEcosystem.DOTNET,
              DeclarationKind.DEPENDENCY,
              name,
              version,
              matcher.group(1),
              name.toLowerCase(Locale.ROOT) + "\n" + condition + "\n" + group(
                  text,
                  matcher.start()
              ),
              file,
              BuildPaths.lineAt(text, matcher.start()),
              flag(version, locked)
          )
      );
    }
    return found;
  }

  private static Declaration.Flag flag(final String version, final boolean locked) {
    if (version != null && version.contains("$(")) {
      return Declaration.Flag.UNDETERMINABLE;
    }
    return locked ? Declaration.Flag.LOCKED : Declaration.Flag.NONE;
  }

  private static String version(final String attributes, final String body) {
    final String inline = attribute(attributes, "Version");
    if (inline != null) {
      return inline;
    }
    final Matcher child = body == null ? null : CHILD_VERSION.matcher(body);
    return child != null && child.find() ? child.group(1) : null;
  }

  /** Returns the condition of the nearest {@code ItemGroup} before {@code index}. */
  private static String group(final String text, final int index) {
    final int open = text.lastIndexOf("<ItemGroup", index);
    final int close = open < 0 ? -1 : text.indexOf('>', open);
    return close < 0 ? "" : String.valueOf(attribute(text.substring(open, close), "Condition"));
  }

  private static String attribute(final String tag, final String name) {
    final Matcher matcher =
        Pattern.compile("\\b" + name + "\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')").matcher(tag);
    if (!matcher.find()) {
      return null;
    }
    return (matcher.group(1) != null ? matcher.group(1) : matcher.group(2)).strip();
  }

  private static String blankComments(final String text) {
    return COMMENT.matcher(text).replaceAll(match -> match.group().replaceAll("[^\\n]", " "));
  }

  private static java.util.Optional<String> member(
      final Path root,
      final String dir,
      final String project
  ) {
    final Path file = root.resolve(dir).resolve(project).normalize();
    final Path parent = file.getParent();
    if (parent == null || !parent.startsWith(root.normalize()) || !Files.isRegularFile(file)) {
      return java.util.Optional.empty();
    }
    final String relative = root.normalize().relativize(parent).toString().replace('\\', '/');
    return relative.equals(dir) ? java.util.Optional.empty() : java.util.Optional.of(relative);
  }

  private static List<Path> files(final Path root, final String dir, final List<String> suffixes) {
    final Path base = root.resolve(dir);
    if (!Files.isDirectory(base)) {
      return List.of();
    }
    try (Stream<Path> entries = Files.list(base)) {
      return entries
          .filter(Files::isRegularFile)
          .filter(entry -> suffixes.stream().anyMatch(entry.getFileName().toString()::endsWith))
          .sorted()
          .toList();
    } catch (final IOException exception) {
      return List.of();
    }
  }
}
