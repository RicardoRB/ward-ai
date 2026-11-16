package dev.ward.application.gate;

import dev.ward.domain.config.GateConfiguration;
import dev.ward.analysis.domain.port.WorkLimit;
import dev.ward.domain.gate.GateProgress;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.QualityGate;
import dev.ward.domain.gate.SupportedQualityGates;
import dev.ward.domain.project.Project;
import java.util.Set;

/**
 * Labels the result of a configured check with the built-in tool type that decides its behavior.
 */
final class TypedQualityGate implements QualityGate {
  // These read the sources and write only under .ward. The other in-process gates may run the
  // build tool for the dependency inventory or read the reports of a command.
  private static final Set<String> INDEPENDENT_TYPES =
      Set.of(
          SupportedQualityGates.ANALYSIS,
          SupportedQualityGates.FORMAT,
          SupportedQualityGates.SECRETS
      );

  private final String type;
  private final QualityGate delegate;
  private final boolean independent;

  /** Labels a gate that does no work of its own, such as a skipped one. */
  TypedQualityGate(final String type, final QualityGate delegate) {
    this(type, delegate, true);
  }

  TypedQualityGate(final String type, final QualityGate delegate, final boolean independent) {
    this.type = type;
    this.delegate = delegate;
    this.independent = independent;
  }

  /**
   * Labels the gate of {@code check}. It is independent when its type only reads sources, it runs
   * no command, and it names no {@code java-classpath}, whose class folders a build may rewrite.
   */
  static TypedQualityGate of(final GateConfiguration check, final QualityGate gate) {
    return new TypedQualityGate(
        check.type(),
        gate,
        INDEPENDENT_TYPES.contains(check.type())
            && check.command().isEmpty()
            && check.javaClasspath() == null
    );
  }

  @Override
  public boolean independent() {
    return independent;
  }

  @Override
  public String name() {
    return delegate.name();
  }

  @Override
  public GateResult execute(final Project project) {
    return delegate.execute(project).withType(type);
  }

  @Override
  public GateResult execute(final Project project, final GateProgress progress) {
    return delegate.execute(project, progress).withType(type);
  }

  @Override
  public GateResult execute(
      final Project project,
      final GateProgress progress,
      final WorkLimit limit
  ) {
    return delegate.execute(project, progress, limit).withType(type);
  }
}
