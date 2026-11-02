package dev.ward.config;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dev.ward.application.init.NodeInputs;
import dev.ward.domain.project.WorkspaceMembers;
import dev.ward.domain.project.node.NodeToolConfigs;
import dev.ward.domain.project.node.PackageManifests;
import dev.ward.infrastructure.project.node.FileSystemNodeSources;
import dev.ward.infrastructure.project.node.FileSystemNodeToolConfigs;
import dev.ward.infrastructure.project.node.NodeWorkspaceMembers;

/** Binds the ports init reads Node projects through. */
@Module
abstract class NodeBindingsModule {
  @Binds
  abstract NodeToolConfigs nodeToolConfigs(FileSystemNodeToolConfigs implementation);

  @Binds
  abstract WorkspaceMembers workspaceMembers(NodeWorkspaceMembers implementation);

  @Provides
  static NodeInputs nodeInputs(
      final PackageManifests manifests,
      final FileSystemNodeSources sources,
      final NodeToolConfigs configs,
      final WorkspaceMembers members) {
    return new NodeInputs(manifests, sources, configs, members);
  }
}
