package dev.ward.config;

import dagger.Module;
import dagger.Provides;
import dev.ward.application.doctor.DoctorPorts;
import dev.ward.application.gate.AnalysisRuleSource;
import dev.ward.domain.config.WardConfigurationReader;
import dev.ward.domain.init.BuildToolConfiguration;
import dev.ward.domain.project.swift.SwiftToolchainInspection;

/** Binds the read ports that {@code ward doctor} checks the project through. */
@Module
interface DoctorBindingsModule {
  @Provides
  static DoctorPorts doctorPorts(
      final WardConfigurationReader configurationReader,
      final BuildToolConfiguration buildTools,
      final SwiftToolchainInspection swift,
      final AnalysisRuleSource rules) {
    return new DoctorPorts(configurationReader, buildTools, swift, rules);
  }
}
