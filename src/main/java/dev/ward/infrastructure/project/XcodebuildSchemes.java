package dev.ward.infrastructure.project;

import dev.ward.domain.config.WardException;
import dev.ward.domain.json.JsonCodec;
import dev.ward.domain.json.JsonCodecException;
import dev.ward.domain.process.CommandExecutor;
import dev.ward.domain.process.CommandResult;
import dev.ward.domain.project.XcodeSchemes;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.inject.Inject;

/**
 * Lists Xcode schemes with {@code xcodebuild -list -json} and reads the macOS-only platform from
 * the {@code SDKROOT} values of the project file. The workspace wins over the project, as Xcode
 * does.
 */
public final class XcodebuildSchemes implements XcodeSchemes {
  private static final String WORKSPACE_SUFFIX = ".xcworkspace";
  private static final String PROJECT_SUFFIX = ".xcodeproj";
  private static final String PROJECT_FILE = "project.pbxproj";
  private static final String SHARED_SCHEMES = "xcshareddata/xcschemes";
  private static final String SCHEME_SUFFIX = ".xcscheme";
  private static final String MACOS_SDK = "macosx";
  private static final Pattern SDK_ROOT = Pattern.compile("SDKROOT\\s*=\\s*([^;\\s]+)\\s*;");
  private final CommandExecutor executor;
  private final JsonCodec codec;

  /** Creates the adapter; {@code executor} runs {@code xcodebuild} and {@code codec} parses it. */
  @Inject
  public XcodebuildSchemes(final CommandExecutor executor, final JsonCodec codec) {
    this.executor = executor;
    this.codec = codec;
  }

  @Override
  public Optional<String> workspace(final Path root) {
    return named(root, WORKSPACE_SUFFIX).findFirst();
  }

  @Override
  public List<String> schemes(final Path root) {
    final List<String> command = new ArrayList<>(List.of("xcodebuild", "-list", "-json"));
    final Optional<String> workspace = workspace(root);
    if (workspace.isPresent()) {
      command.addAll(List.of("-workspace", workspace.get()));
    } else {
      command.addAll(
          List.of("-project", named(root, PROJECT_SUFFIX).findFirst().orElseThrow(this::noXcode)));
    }
    final CommandResult result = executor.execute(List.copyOf(command), root);
    if (result.hasExecutionError() || !result.succeeded()) {
      throw new WardException("xcodebuild -list failed; Ward could not read the Xcode schemes.");
    }
    // xcodebuild also lists schemes Xcode creates for each target; only the shared ones are the
    // project's.
    final List<String> shared = sharedSchemes(root);
    return schemesOf(result.stdout()).stream().filter(shared::contains).toList();
  }

  /** Returns the names of the shared {@code .xcscheme} files of the root's containers. */
  private static List<String> sharedSchemes(final Path root) {
    final List<String> names = new ArrayList<>();
    for (final String container : List.of(PROJECT_SUFFIX, WORKSPACE_SUFFIX)) {
      for (final String name : named(root, container).toList()) {
        names.addAll(schemeNames(root.resolve(name).resolve(SHARED_SCHEMES)));
      }
    }
    return List.copyOf(names);
  }

  private static List<String> schemeNames(final Path directory) {
    if (!Files.isDirectory(directory)) {
      return List.of();
    }
    try (Stream<Path> files = Files.list(directory)) {
      return files
          .map(file -> directory.relativize(file).toString())
          .filter(name -> name.endsWith(SCHEME_SUFFIX))
          .map(name -> name.substring(0, name.length() - SCHEME_SUFFIX.length()))
          .toList();
    } catch (IOException e) {
      throw new WardException("Could not list " + directory + ": " + e.getMessage(), e);
    }
  }

  @Override
  public boolean macOsOnly(final Path root) {
    final Path file =
        named(root, PROJECT_SUFFIX)
            .findFirst()
            .map(project -> root.resolve(project).resolve(PROJECT_FILE))
            .orElseThrow(this::noXcode);
    final List<String> sdks = sdkRoots(read(file));
    return !sdks.isEmpty() && sdks.stream().allMatch(MACOS_SDK::equals);
  }

  /** Reads the schemes of the workspace, or of the project when there is no workspace. */
  private List<String> schemesOf(final String json) {
    try {
      final Object parsed = codec.fromJson(json, Object.class);
      final Map<?, ?> list = parsed instanceof Map<?, ?> map ? map : Map.of();
      final Object container =
          list.get("workspace") != null ? list.get("workspace") : list.get("project");
      final Object schemes = container instanceof Map<?, ?> named ? named.get("schemes") : null;
      return schemes instanceof List<?> names
          ? names.stream().filter(String.class::isInstance).map(String.class::cast).toList()
          : List.of();
    } catch (JsonCodecException e) {
      throw new WardException("xcodebuild -list printed JSON Ward could not read.", e);
    }
  }

  private static List<String> sdkRoots(final String pbxproj) {
    final Matcher matcher = SDK_ROOT.matcher(pbxproj);
    final List<String> sdks = new ArrayList<>();
    while (matcher.find()) {
      sdks.add(matcher.group(1));
    }
    return List.copyOf(sdks);
  }

  private static Stream<String> named(final Path root, final String suffix) {
    try (Stream<Path> entries = Files.list(root)) {
      return entries
          .map(entry -> root.relativize(entry).toString())
          .filter(name -> name.endsWith(suffix))
          .sorted()
          .toList()
          .stream();
    } catch (IOException e) {
      throw new WardException("Could not list " + root + ": " + e.getMessage(), e);
    }
  }

  private static String read(final Path file) {
    try {
      return Files.readString(file);
    } catch (IOException e) {
      throw new WardException("Could not read " + file + ": " + e.getMessage(), e);
    }
  }

  private WardException noXcode() {
    return new WardException("No Xcode project or workspace was found at the project root.");
  }
}
