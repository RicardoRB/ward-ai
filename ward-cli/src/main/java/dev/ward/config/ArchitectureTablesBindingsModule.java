package dev.ward.config;

import dagger.Binds;
import dagger.Module;
import dagger.multibindings.IntoMap;
import dev.ward.domain.init.ArchitectureTables;
import dev.ward.domain.project.Toolchain;
import dev.ward.infrastructure.init.dotnet.DotnetArchitectureTables;
import dev.ward.infrastructure.init.node.NodeArchitectureTables;
import dev.ward.infrastructure.init.python.PythonArchitectureTables;
import dev.ward.infrastructure.init.rust.RustArchitectureTables;
import dev.ward.infrastructure.init.swift.SwiftArchitectureTables;

/** The architecture tables of each toolchain other than the JVM, which is the default. */
@Module
interface ArchitectureTablesBindingsModule {
  @Binds
  @IntoMap
  @ToolchainKey(Toolchain.NODE)
  ArchitectureTables node(NodeArchitectureTables tables);

  @Binds
  @IntoMap
  @ToolchainKey(Toolchain.PYTHON)
  ArchitectureTables python(PythonArchitectureTables tables);

  @Binds
  @IntoMap
  @ToolchainKey(Toolchain.RUST)
  ArchitectureTables rust(RustArchitectureTables tables);

  @Binds
  @IntoMap
  @ToolchainKey(Toolchain.SWIFT)
  ArchitectureTables swift(SwiftArchitectureTables tables);

  @Binds
  @IntoMap
  @ToolchainKey(Toolchain.DOTNET)
  ArchitectureTables dotnet(DotnetArchitectureTables tables);
}
