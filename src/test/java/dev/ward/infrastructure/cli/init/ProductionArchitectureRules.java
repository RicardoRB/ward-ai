package dev.ward.infrastructure.cli.init;

import dev.ward.infrastructure.init.ArchitectureTestGenerator;
import dev.ward.infrastructure.init.ToolchainArchitectureRules;
import dev.ward.infrastructure.init.node.DependencyCruiserConfigGenerator;
import dev.ward.infrastructure.init.python.ImportLinterConfigGenerator;
import dev.ward.infrastructure.init.swift.HarmonizeRulesGenerator;
import dev.ward.infrastructure.project.FileSystemNodeToolConfigs;
import dev.ward.infrastructure.project.FileSystemPythonToolConfigs;
import dev.ward.infrastructure.project.FileSystemSwiftToolConfigs;

/** The architecture rules router with the generators the production wiring injects. */
final class ProductionArchitectureRules {
  private ProductionArchitectureRules() {}

  /** Returns the router that init uses, with every toolchain's real generator. */
  static ToolchainArchitectureRules create() {
    return new ToolchainArchitectureRules(
        new ArchitectureTestGenerator(),
        new DependencyCruiserConfigGenerator(new FileSystemNodeToolConfigs()),
        new ImportLinterConfigGenerator(new FileSystemPythonToolConfigs()),
        new HarmonizeRulesGenerator(new FileSystemSwiftToolConfigs()));
  }
}
