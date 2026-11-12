package dev.ward.config;

import dagger.Module;

@Module(
    includes = {
      ProcessBindingsModule.class,
      ProjectBindingsModule.class,
      InitBindingsModule.class,
      TraceabilityBindingsModule.class,
      ProvenanceBindingsModule.class,
      GateBindingsModule.class,
      GateExtensionBindingsModule.class,
      RuleBindingsModule.class,
      GateExtensionsModule.class,
      FormatterBindingsModule.class,
      ReportBindingsModule.class,
      DoctorBindingsModule.class,
      DependencyBindingsModule.class,
      BuildPolicyBindingsModule.class,
      RulesBindingsModule.class,
      VulnerabilityBindingsModule.class
    })
interface PortBindingsModule {}
