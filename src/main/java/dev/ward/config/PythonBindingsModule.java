package dev.ward.config;

import dagger.Binds;
import dagger.Module;
import dev.ward.domain.project.PythonToolConfigs;
import dev.ward.infrastructure.project.FileSystemPythonToolConfigs;

/** Binds the port init reads Python projects' tool configs through. */
@Module
abstract class PythonBindingsModule {
  @Binds
  abstract PythonToolConfigs pythonToolConfigs(FileSystemPythonToolConfigs implementation);
}
