package dev.ward.config;

import dagger.Module;
import dagger.Provides;
import dev.ward.application.init.InitPorts;
import dev.ward.application.init.NodeInputs;
import dev.ward.application.init.SwiftPorts;
import dev.ward.application.init.ToolchainPorts;
import dev.ward.domain.doctor.EnvironmentInspection;
import dev.ward.domain.init.BuildModulePlanning;
import dev.ward.domain.init.BuildToolConfiguration;
import dev.ward.domain.init.CompatibilityInspection;
import dev.ward.domain.project.python.PythonToolConfigs;
import dev.ward.infrastructure.init.BuildToolConfigurator;
import dev.ward.infrastructure.init.JvmBuildToolConfigurator;
import dev.ward.infrastructure.project.gradle.GradleCompatibilityInspection;
import javax.inject.Named;

/** Build-file adapters, one per supported toolchain, behind the routing configurator. */
@Module(includes = BuildToolAdaptersModule.class)
final class BuildToolBindingsModule {
  private BuildToolBindingsModule() {}

  @Provides
  static BuildToolConfiguration buildToolConfiguration(final BuildToolConfigurator router) {
    return router;
  }

  @Provides
  @Named("gradle")
  static CompatibilityInspection gradleCompatibilityInspection(
      final GradleCompatibilityInspection implementation
  ) {
    return implementation;
  }

  @Provides
  static BuildModulePlanning buildModulePlanning(final JvmBuildToolConfigurator jvm) {
    return jvm;
  }

  @Provides
  static ToolchainPorts toolchainPorts(
      final SwiftPorts swift,
      final BuildModulePlanning modulePlanning
  ) {
    return new ToolchainPorts(swift, modulePlanning);
  }

  @Provides
  static InitPorts initPorts(
      final EnvironmentInspection environment,
      final NodeInputs node,
      final BuildToolConfiguration buildTools,
      final PythonToolConfigs python,
      final ToolchainPorts toolchains
  ) {
    return new InitPorts(environment, node, buildTools, python, toolchains);
  }
}
