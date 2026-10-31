package dev.ward.config;

import dagger.Binds;
import dagger.Module;
import dev.ward.domain.project.SwiftToolchainInspection;
import dev.ward.infrastructure.project.ProcessSwiftToolchainInspection;

/** Binds the port doctor reads the Xcode version through. */
@Module
abstract class SwiftBindingsModule {
  @Binds
  abstract SwiftToolchainInspection swiftToolchainInspection(
      ProcessSwiftToolchainInspection implementation);
}
