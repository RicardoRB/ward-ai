package dev.ward.config;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dev.ward.application.init.SwiftPorts;
import dev.ward.domain.project.SwiftSourceDirectories;
import dev.ward.domain.project.SwiftToolConfigs;
import dev.ward.domain.project.SwiftToolchainInspection;
import dev.ward.domain.project.XcodeSchemes;
import dev.ward.infrastructure.project.FileSystemSwiftSourceDirectories;
import dev.ward.infrastructure.project.FileSystemSwiftToolConfigs;
import dev.ward.infrastructure.project.ProcessSwiftToolchainInspection;
import dev.ward.infrastructure.project.XcodebuildSchemes;

/** Binds the ports doctor and init read Swift projects and their tool chain through. */
@Module
abstract class SwiftBindingsModule {
  @Binds
  abstract SwiftToolchainInspection swiftToolchainInspection(
      ProcessSwiftToolchainInspection implementation);

  @Binds
  abstract SwiftToolConfigs swiftToolConfigs(FileSystemSwiftToolConfigs implementation);

  @Binds
  abstract XcodeSchemes xcodeSchemes(XcodebuildSchemes implementation);

  @Binds
  abstract SwiftSourceDirectories swiftSourceDirectories(
      FileSystemSwiftSourceDirectories implementation);

  /** Groups the Swift read ports init takes as one binding. */
  @Provides
  static SwiftPorts swiftPorts(
      final XcodeSchemes xcode,
      final SwiftToolConfigs swiftConfigs,
      final SwiftSourceDirectories swiftSources) {
    return new SwiftPorts(xcode, swiftConfigs, swiftSources);
  }
}
