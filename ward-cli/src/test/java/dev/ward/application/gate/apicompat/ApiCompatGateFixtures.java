package dev.ward.application.gate.apicompat;

import dev.ward.analysis.domain.api.ApiSurfaceReader;
import dev.ward.analysis.domain.api.ApiType;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.jvm.types.JavaApiSurfaceReader;
import dev.ward.analysis.domain.jvm.types.KotlinApiSurfaceReader;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import dev.ward.domain.config.ApiCompatOptions;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.WorkingFiles;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.analysis.jvm.JvmSourceLayouts;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** A repository with the tag {@code v1.0.0}, a working tree, and the gate that compares them. */
final class ApiCompatGateFixtures {
  static final Path ROOT = Path.of("/project");
  static final Project PROJECT =
      new Project(ROOT, Language.JAVA, BuildTool.MAVEN, Framework.SPRING_BOOT);
  static final String CLIENT_PATH = "src/main/java/p/Client.java";
  static final String BASE_COMMIT = "commit-v1.0.0";
  static final String CLIENT =
      "package p;\npublic class Client {\n  public void close() {}\n  public void open() {}\n}\n";
  static final String CLIENT_WITHOUT_CLOSE =
      "package p;\npublic class Client {\n  public void open() {}\n}\n";
  static final String BROKEN = "package p;\npublic class Client { void m( }\n";

  final FakeRevisions git = new FakeRevisions().ref("HEAD", "head").tag("v1.0.0");
  final Map<String, String> working = new TreeMap<>();

  void baseline(final String path, final String text) {
    git.file(BASE_COMMIT, path, text);
  }

  GateResult run(final ApiCompatOptions options) {
    return new ApiCompatGate("api-compat", options, ports(git)).execute(PROJECT);
  }

  GateResult run() {
    return run(ApiCompatOptions.DEFAULTS);
  }

  ApiCompatPorts ports(final FakeRevisions revisions) {
    final WorkingFiles files =
        new WorkingFiles() {
          @Override
          public List<String> files(final Path root, final String directory) {
            final String prefix = directory + "/";
            return working.keySet().stream()
                .filter(path -> path.startsWith(prefix))
                .map(path -> path.substring(prefix.length()))
                .toList();
          }

          @Override
          public Optional<String> read(final Path root, final String path) {
            return Optional.ofNullable(working.get(path));
          }
        };
    return new ApiCompatPorts(
        revisions,
        files,
        JvmSourceLayouts::mainRoots,
        (root, units) -> {
          final List<ApiType> found = new ArrayList<>();
          for (final ApiSurfaceReader reader :
              List.of(new JavaApiSurfaceReader(), new KotlinApiSurfaceReader())) {
            final List<SourceUnit> own =
                units.stream().filter(unit -> unit.language() == reader.language()).toList();
            if (!own.isEmpty()) {
              found.addAll(
                  reader.read(
                      own,
                      new AntlrLanguageFrontend(),
                      name ->
                          ClassLoader.getSystemResource(name.replace('.', '/') + ".class")
                              != null));
            }
          }
          return found;
        });
  }
}
