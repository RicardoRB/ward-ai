package dev.ward.config;

import dagger.Module;
import dagger.Provides;
import dev.ward.application.doctor.DoctorPorts;
import dev.ward.application.init.InitPorts;
import dev.ward.application.init.NodeInputs;
import dev.ward.domain.config.WardConfigurationReader;
import dev.ward.domain.doctor.EnvironmentInspection;
import dev.ward.domain.init.BuildToolConfiguration;
import dev.ward.domain.init.CompatibilityInspection;
import dev.ward.domain.project.PythonToolConfigs;
import dev.ward.domain.project.Toolchain;
import dev.ward.domain.traceability.TraceabilityPrerequisites;
import dev.ward.infrastructure.init.BuildToolConfigurator;
import dev.ward.infrastructure.init.JvmBuildToolConfigurator;
import dev.ward.infrastructure.init.NodeBuildToolConfigurator;
import dev.ward.infrastructure.init.PythonBuildToolConfigurator;
import dev.ward.infrastructure.project.GradleCompatibilityInspection;
import java.util.Map;
import javax.inject.Named;

/** Build-file adapters, one per supported toolchain, behind the routing configurator. */
@Module
final class BuildToolBindingsModule {
  private BuildToolBindingsModule() {}

  @Provides
  static BuildToolConfiguration buildToolConfiguration(final BuildToolConfigurator router) {
    return router;
  }

  @Provides
  @Named("gradle")
  static CompatibilityInspection gradleCompatibilityInspection(
      final GradleCompatibilityInspection implementation) {
    return implementation;
  }

  @Provides
  static InitPorts initPorts(
      final EnvironmentInspection environment,
      final NodeInputs node,
      final BuildToolConfiguration buildTools,
      final PythonToolConfigs python) {
    return new InitPorts(environment, node, buildTools, python);
  }

  @Provides
  static DoctorPorts doctorPorts(
      final WardConfigurationReader configurationReader,
      final TraceabilityPrerequisites traceabilityPrerequisites,
      final BuildToolConfiguration buildTools) {
    return new DoctorPorts(configurationReader, traceabilityPrerequisites, buildTools);
  }

  @Provides
  static Map<Toolchain, BuildToolConfiguration> buildToolAdapters(
      final JvmBuildToolConfigurator jvm,
      final NodeBuildToolConfigurator node,
      final PythonBuildToolConfigurator python) {
    return Map.of(Toolchain.JVM, jvm, Toolchain.NODE, node, Toolchain.PYTHON, python);
  }
}
