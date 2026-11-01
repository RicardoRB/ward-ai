package dev.ward.config;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dev.ward.application.init.InitProjectFiles;
import dev.ward.domain.init.ArchitectureTestGeneration;
import dev.ward.domain.init.BuildToolConfiguration;
import dev.ward.domain.init.ConfigurationStorage;
import dev.ward.domain.init.OwnedSectionGeneration;
import dev.ward.domain.init.QualityHookGeneration;
import dev.ward.domain.init.RawTerminal;
import dev.ward.domain.init.RuleFileGeneration;
import dev.ward.domain.init.ToolInstaller;
import dev.ward.domain.project.Toolchain;
import dev.ward.domain.terminal.TerminalCapabilities;
import dev.ward.infrastructure.init.CompositeToolInstaller;
import dev.ward.infrastructure.init.FileConfigurationStorage;
import dev.ward.infrastructure.init.OwnedSectionWriter;
import dev.ward.infrastructure.init.PresetRuleFileWriter;
import dev.ward.infrastructure.init.QualityHookInstaller;
import dev.ward.infrastructure.init.RuleFileTargets;
import dev.ward.infrastructure.init.ToolchainArchitectureRules;
import dev.ward.infrastructure.init.node.NodeRuleFileTargets;
import dev.ward.infrastructure.init.python.PythonRuleFileTargets;
import dev.ward.infrastructure.init.swift.SwiftRuleFileTargets;
import dev.ward.infrastructure.terminal.JdkTerminalCapabilities;
import dev.ward.infrastructure.terminal.PosixRawTerminal;
import java.util.Map;

@Module(
    includes = {
      BuildToolBindingsModule.class,
      NodeBindingsModule.class,
      PythonBindingsModule.class,
      SwiftBindingsModule.class
    })
interface InitBindingsModule {
  @Binds
  ConfigurationStorage configurationStorage(FileConfigurationStorage implementation);

  @Binds
  RawTerminal rawTerminal(PosixRawTerminal implementation);

  @Binds
  TerminalCapabilities terminalCapabilities(JdkTerminalCapabilities implementation);

  @Binds
  ArchitectureTestGeneration architectureTestGeneration(ToolchainArchitectureRules implementation);

  @Binds
  RuleFileGeneration ruleFileGeneration(PresetRuleFileWriter implementation);

  @Binds
  QualityHookGeneration qualityHookGeneration(QualityHookInstaller implementation);

  @Binds
  OwnedSectionGeneration ownedSectionGeneration(OwnedSectionWriter implementation);

  @Binds
  ToolInstaller toolInstaller(CompositeToolInstaller implementation);

  /** The rule files of each toolchain that has its own; a JVM project has none here. */
  @Provides
  static Map<Toolchain, RuleFileTargets> ruleFileTargets(
      final NodeRuleFileTargets node,
      final PythonRuleFileTargets python,
      final SwiftRuleFileTargets swift) {
    return Map.of(Toolchain.NODE, node, Toolchain.PYTHON, python, Toolchain.SWIFT, swift);
  }

  @Provides
  static InitProjectFiles initProjectFiles(
      final BuildToolConfiguration buildTools,
      final RuleFileGeneration ruleFiles,
      final ArchitectureTestGeneration architectureTests,
      final ConfigurationStorage configuration,
      final OwnedSectionGeneration sections) {
    return new InitProjectFiles(buildTools, ruleFiles, architectureTests, configuration, sections);
  }
}
