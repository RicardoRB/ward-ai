package dev.ward.config;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dev.ward.application.init.NodeInputs;
import dev.ward.domain.project.NodeToolConfigs;
import dev.ward.domain.project.PackageManifests;
import dev.ward.infrastructure.project.FileSystemNodeSources;
import dev.ward.infrastructure.project.FileSystemNodeToolConfigs;

/** Binds the ports init reads Node projects through. */
@Module
abstract class NodeBindingsModule {
  @Binds
  abstract NodeToolConfigs nodeToolConfigs(FileSystemNodeToolConfigs implementation);

  @Provides
  static NodeInputs nodeInputs(
      final PackageManifests manifests,
      final FileSystemNodeSources sources,
      final NodeToolConfigs configs) {
    return new NodeInputs(manifests, sources, configs);
  }
}
