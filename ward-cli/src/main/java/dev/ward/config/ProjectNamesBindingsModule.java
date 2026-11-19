package dev.ward.config;

import dagger.Binds;
import dagger.Module;
import dagger.multibindings.IntoMap;
import dev.ward.domain.project.ProjectNameReader;
import dev.ward.domain.project.Toolchain;
import dev.ward.infrastructure.project.dotnet.DotnetProjectName;
import dev.ward.infrastructure.project.jvm.JvmProjectName;
import dev.ward.infrastructure.project.node.NodeProjectName;
import dev.ward.infrastructure.project.python.PythonProjectName;
import dev.ward.infrastructure.project.rust.CargoProjectName;
import dev.ward.infrastructure.project.swift.SwiftProjectName;

/** The project name reader of each toolchain, which the SBOM uses for its root component. */
@Module
interface ProjectNamesBindingsModule {
  @Binds
  @IntoMap
  @ToolchainKey(Toolchain.JVM)
  ProjectNameReader jvm(JvmProjectName reader);

  @Binds
  @IntoMap
  @ToolchainKey(Toolchain.NODE)
  ProjectNameReader node(NodeProjectName reader);

  @Binds
  @IntoMap
  @ToolchainKey(Toolchain.PYTHON)
  ProjectNameReader python(PythonProjectName reader);

  @Binds
  @IntoMap
  @ToolchainKey(Toolchain.SWIFT)
  ProjectNameReader swift(SwiftProjectName reader);

  @Binds
  @IntoMap
  @ToolchainKey(Toolchain.RUST)
  ProjectNameReader rust(CargoProjectName reader);

  @Binds
  @IntoMap
  @ToolchainKey(Toolchain.DOTNET)
  ProjectNameReader dotnet(DotnetProjectName reader);
}
