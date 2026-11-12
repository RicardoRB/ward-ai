package dev.ward.infrastructure.buildpolicy;

import dev.ward.domain.buildpolicy.BuildModel;
import dev.ward.domain.buildpolicy.BuildModelReader;
import dev.ward.domain.buildpolicy.Declaration;
import dev.ward.infrastructure.buildpolicy.gradle.GradleBuildReader;
import dev.ward.infrastructure.buildpolicy.maven.MavenBuildReader;
import dev.ward.infrastructure.buildpolicy.node.NodeBuildReader;
import dev.ward.infrastructure.buildpolicy.python.PythonBuildReader;
import dev.ward.infrastructure.json.ForyJsonCodec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** A project folder that a test fills and reads with every build policy reader. */
final class ReaderProject {
  static final String HEAD =
      "<project><modelVersion>4.0.0</modelVersion><groupId>g</groupId><artifactId>a</artifactId>"
          + "<version>1</version>";

  private final Path root;

  ReaderProject(final Path root) {
    this.root = root;
  }

  BuildModel all() {
    return new FileSystemBuildFiles(
            List.<BuildModelReader>of(
                new MavenBuildReader(),
                new GradleBuildReader(),
                new NodeBuildReader(new ForyJsonCodec()),
                new PythonBuildReader()))
        .read(root);
  }

  void write(final String name, final String text) throws IOException {
    final Path file = root.resolve(name);
    Files.createDirectories(file.getParent());
    Files.writeString(file, text);
  }

  List<Declaration> named(final String name) {
    return all().declarations().stream().filter(d -> d.name().endsWith(name)).toList();
  }
}
