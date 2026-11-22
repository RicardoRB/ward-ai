package dev.ward.config;

import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoMap;
import dev.ward.domain.init.BuildToolConfiguration;
import dev.ward.domain.project.Toolchain;
import dev.ward.infrastructure.init.JvmBuildToolConfigurator;
import dev.ward.infrastructure.init.dotnet.DotnetBuildToolConfigurator;
import dev.ward.infrastructure.init.node.NodeBuildToolConfigurator;
import dev.ward.infrastructure.init.python.PythonBuildToolConfigurator;
import dev.ward.infrastructure.init.rust.CargoBuildToolConfigurator;
import dev.ward.infrastructure.init.swift.SwiftBuildToolConfigurator;

/** The build-file adapter of each toolchain, keyed by the toolchain it serves. */
@Module
final class BuildToolAdaptersModule {
  private BuildToolAdaptersModule() {}

  @Provides
  @IntoMap
  @ToolchainKey(Toolchain.JVM)
  static BuildToolConfiguration jvmAdapter(final JvmBuildToolConfigurator adapter) {
    return adapter;
  }

  @Provides
  @IntoMap
  @ToolchainKey(Toolchain.NODE)
  static BuildToolConfiguration nodeAdapter(final NodeBuildToolConfigurator adapter) {
    return adapter;
  }

  @Provides
  @IntoMap
  @ToolchainKey(Toolchain.PYTHON)
  static BuildToolConfiguration pythonAdapter(final PythonBuildToolConfigurator adapter) {
    return adapter;
  }

  @Provides
  @IntoMap
  @ToolchainKey(Toolchain.RUST)
  static BuildToolConfiguration rustAdapter(final CargoBuildToolConfigurator adapter) {
    return adapter;
  }

  @Provides
  @IntoMap
  @ToolchainKey(Toolchain.SWIFT)
  static BuildToolConfiguration swiftAdapter(final SwiftBuildToolConfigurator adapter) {
    return adapter;
  }

  @Provides
  @IntoMap
  @ToolchainKey(Toolchain.DOTNET)
  static BuildToolConfiguration dotnetAdapter(final DotnetBuildToolConfigurator adapter) {
    return adapter;
  }
}
