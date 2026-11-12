package dev.ward.config;

import dagger.Module;
import dagger.Provides;
import dev.ward.domain.buildpolicy.BuildFiles;
import dev.ward.domain.buildpolicy.BuildModelReader;
import dev.ward.domain.json.JsonCodec;
import dev.ward.infrastructure.buildpolicy.FileSystemBuildFiles;
import dev.ward.infrastructure.buildpolicy.gradle.GradleBuildReader;
import dev.ward.infrastructure.buildpolicy.maven.MavenBuildReader;
import dev.ward.infrastructure.buildpolicy.node.NodeBuildReader;
import dev.ward.infrastructure.buildpolicy.python.PythonBuildReader;
import java.util.List;

/** Binds the build policy readers, one per build tool, with an explicit constructor graph. */
@Module
interface BuildPolicyBindingsModule {
  /** Reads the build files of every build tool Ward supports. */
  @Provides
  static BuildFiles buildFiles(final JsonCodec codec) {
    final List<BuildModelReader> readers =
        List.of(
            new MavenBuildReader(),
            new GradleBuildReader(),
            new NodeBuildReader(codec),
            new PythonBuildReader());
    return new FileSystemBuildFiles(readers);
  }
}
