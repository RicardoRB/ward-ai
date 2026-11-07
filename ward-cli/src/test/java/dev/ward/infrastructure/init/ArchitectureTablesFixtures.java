package dev.ward.infrastructure.init;

import dev.ward.domain.init.ArchitectureTables;
import dev.ward.infrastructure.init.node.NodeArchitectureTables;
import dev.ward.infrastructure.init.python.PythonArchitectureTables;
import dev.ward.infrastructure.init.swift.SwiftArchitectureTables;

/** Builds the architecture tables of every toolchain, as the production wiring does. */
public final class ArchitectureTablesFixtures {

  private ArchitectureTablesFixtures() {}

  /** Returns the tables that answer each project with its own toolchain's layout. */
  public static ArchitectureTables create() {
    return new ToolchainArchitectureTables(
        new JavaArchitectureTables(),
        new NodeArchitectureTables(),
        new PythonArchitectureTables(),
        new SwiftArchitectureTables());
  }
}
