package dev.ward.application.init;

import dev.ward.application.init.python.PythonTools;

/** A violating fixture: an init root class that names the Python toolchain outside the registry. */
public final class ArchitectureFixtureRootReachesPython {
  public PythonTools tools;
}
