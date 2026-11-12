package dev.ward.config;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dev.ward.application.gate.apicompat.ApiSurfaces;
import dev.ward.domain.gate.MainSourceRoots;
import dev.ward.domain.gate.WorkingFiles;
import dev.ward.infrastructure.analysis.jvm.JvmApiSurfaces;
import dev.ward.infrastructure.analysis.jvm.JvmSourceLayouts;
import dev.ward.infrastructure.gate.FileSystemWorkingFiles;

/** Binds the adapters the native {@code api-compat} gate reads sources through. */
@Module
interface ApiCompatBindingsModule {
  @Binds
  WorkingFiles workingFiles(FileSystemWorkingFiles implementation);

  @Binds
  ApiSurfaces apiSurfaces(JvmApiSurfaces implementation);

  /** The main source roots come from the text of the build file, read at any revision. */
  @Provides
  static MainSourceRoots mainSourceRoots() {
    return JvmSourceLayouts::mainRoots;
  }
}
