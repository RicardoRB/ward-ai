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
            BlockChecks.checks(),
            ClassDesignChecks.checks(),
            CodingChecks.checks(),
            FieldChecks.checks(),
            CheckstyleDesignChecks.checks(),
            CheckstyleCodingChecks.checks(),
            DesignChecks.checks(),
            DeclarationChecks.checks(),
            ErrorProneChecks.checks(),
            ImportChecks.checks(),
            KotlinBugChecks.checks(),
            JavadocChecks.checks(),
            JavadocLayoutChecks.checks(),
            MetricChecks.checks(),
            ModifierChecks.checks(),
            ObjectMethodChecks.checks(),
            ReviewChecks.checks(),
            StrictDeclarationChecks.checks(),
            TypeAwareChecks.checks()));
  }
}
