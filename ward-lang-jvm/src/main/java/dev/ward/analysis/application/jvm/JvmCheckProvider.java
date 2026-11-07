package dev.ward.analysis.application.jvm;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.CheckProvider;
import dev.ward.analysis.application.rule.CheckRegistry;
import java.util.List;
import java.util.Map;

/**
 * The declarative checks that only Java and Kotlin rules use. Add a Java check to one of the
 * families here, or a new family to {@link #checks()}.
 */
public final class JvmCheckProvider implements CheckProvider {

  @Override
  public Map<String, CheckFactory> checks() {
    return CheckRegistry.union(
        List.of(
            CodingChecks.checks(),
            CheckstyleDesignChecks.checks(),
            DesignChecks.checks(),
            ImportChecks.checks(),
            JavadocChecks.checks(),
            MetricChecks.checks(),
            ReviewChecks.checks(),
            TypeAwareChecks.checks()));
  }
}
