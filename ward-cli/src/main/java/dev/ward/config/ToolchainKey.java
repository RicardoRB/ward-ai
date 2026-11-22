package dev.ward.config;

import dagger.MapKey;
import dev.ward.domain.project.Toolchain;

/** Keys a Dagger map entry by the toolchain it serves. */
@MapKey
@interface ToolchainKey {
  /** Returns the toolchain of the entry. */
  Toolchain value();
}
