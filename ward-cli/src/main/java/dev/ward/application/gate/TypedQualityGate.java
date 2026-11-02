package dev.ward.application.gate;

import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.QualityGate;
import dev.ward.domain.project.Project;

/**
 * Labels the result of a configured check with the built-in tool type that decides its behavior.
 */
final class TypedQualityGate implements QualityGate {
  private final String type;
  private final QualityGate delegate;

  TypedQualityGate(final String type, final QualityGate delegate) {
    this.type = type;
    this.delegate = delegate;
  }

  @Override
  public String name() {
    return delegate.name();
  }

  @Override
  public GateResult execute(final Project project) {
    return delegate.execute(project).withType(type);
  }
}
