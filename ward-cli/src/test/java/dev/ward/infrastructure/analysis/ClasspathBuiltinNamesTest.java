package dev.ward.infrastructure.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.analysis.domain.port.AnalysisFailedException;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ClasspathBuiltinNamesTest {

  @Test
  void pythonProvidesItsBuiltinsAndModuleNames() {
    assertThat(ClasspathBuiltinNames.python().of(Set.of()))
        .contains("print", "len", "__name__", "ValueError", "super");
  }

  @Test
  void javascriptAlwaysHasEcmaScriptAndAddsTheDefaultEnvironments() {
    final Set<String> names = ClasspathBuiltinNames.javascript().of(Set.of());

    assertThat(names).contains("Array", "Promise", "window", "process", "console", "undefined");
  }

  @Test
  void aRequestedEnvironmentReplacesTheDefaultsButKeepsEcmaScript() {
    final Set<String> names = ClasspathBuiltinNames.javascript().of(Set.of("node"));

    assertThat(names).contains("Array", "process").doesNotContain("window");
  }

  @Test
  void typescriptAddsThePredefinedTypes() {
    assertThat(ClasspathBuiltinNames.typescript().of(Set.of()))
        .contains("any", "Partial", "Record");
    assertThat(ClasspathBuiltinNames.javascript().of(Set.of())).doesNotContain("Partial");
  }

  @Test
  void anUnknownEnvironmentIsAnError() {
    assertThatThrownBy(() -> ClasspathBuiltinNames.javascript().of(Set.of("deno")))
        .isInstanceOf(AnalysisFailedException.class)
        .hasMessageContaining("deno");
  }

  @Test
  void theJvmAndSwiftLanguagesProvideTheirStandardLibraries() {
    assertThat(ClasspathBuiltinNames.java().of(Set.of())).contains("String", "System", "Math");
    assertThat(ClasspathBuiltinNames.kotlin().of(Set.of()))
        .contains("println", "listOf", "require");
    assertThat(ClasspathBuiltinNames.swift().of(Set.of())).contains("print", "Int", "fatalError");
  }
}
