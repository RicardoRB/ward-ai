package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.SpanFacts;
import java.util.List;
import java.util.Set;

/**
 * A class annotated {@code @AutoFactory} that has a constructor annotated {@code @Inject}, or a
 * constructor that has both annotations.
 */
public final class AutoFactoryInjected implements DeclarationFactTest {

  private static final String AUTO_FACTORY = "com.google.auto.factory.AutoFactory";
  private static final String CONSTRUCTOR = "<init>";
  private static final Set<String> INJECT =
      Set.of("javax.inject.Inject", "jakarta.inject.Inject", "com.google.inject.Inject");

  @Override
  public boolean reports(final SyntaxNode declaration, final SpanFacts facts) {
    if (facts.members() == null) {
      return false;
    }
    final boolean onClass = facts.members().annotations().contains(AUTO_FACTORY);
    final List<CallTarget> constructors =
        facts.members().methods().stream()
            .filter(method -> CONSTRUCTOR.equals(method.name()))
            .toList();
    return constructors.stream()
        .filter(constructor -> constructor.annotations().stream().anyMatch(INJECT::contains))
        .anyMatch(constructor -> onClass || constructor.annotations().contains(AUTO_FACTORY));
  }
}
