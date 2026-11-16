package dev.ward.analysis.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import dev.ward.analysis.domain.graph.DependencyGraph;
import dev.ward.analysis.domain.graph.ProjectFiles;
import dev.ward.analysis.domain.ir.Reference;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.rule.ArchitectureConstraint.Kind;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The {@code external} selector of {@code must-not-depend-on}, through {@link GraphRules}. */
class ArchitectureExternalRuleTest {
  private static final String DOMAIN = "src/shop/domain/order";
  private static final String OTHER = "src/shop/web/page";
  private static final Span SPAN = new Span(3, 1, 3, 9, 20, 28);

  @ParameterizedTest
  @CsvSource(
      {
          "import:org.springframework.stereotype.Service, org.springframework",
          "on-demand:org.springframework.stereotype, org.springframework",
          "static:org.springframework.util.Assert.notNull, org.springframework.util",
          "static:express, express",
          "type:@nestjs/common, @nestjs",
          "require:lodash/fp, lodash",
          "dynamic:node:fs, node:fs",
          "abs:django.db, django",
          "from:0:django.db:models, django.db.models",
          "module:UIKit, UIKit"
      }
  )
  void anImportOfANamedPackageIsAFindingAtTheImport(final String specifier, final String entry) {
    final List<GraphMatch> matches = matches(entry, DOMAIN, specifier);

    assertThat(matches).hasSize(1);
    assertThat(matches.getFirst().file()).isEqualTo(DOMAIN);
    assertThat(matches.getFirst().match().span()).isEqualTo(SPAN);
    assertThat(matches.getFirst().match().message())
        .startsWith("no-framework: " + DOMAIN + " must not depend on ")
        .endsWith(", because the reason");
  }

  @ParameterizedTest
  @CsvSource(
      {
          "static:react-dom, react",
          "import:org.springframeworks.Other, org.springframework",
          "static:./express, express",
          "static:../express, .",
          "from:1:django:models, django",
          "name:org.springframework.Service, org.springframework",
          "package:org.springframework, org.springframework",
          "alias:S=org.springframework.Service, org.springframework"
      }
  )
  void otherNamesRelativeImportsAndUsesAreNotReported(final String specifier, final String entry) {
    assertThat(matches(entry, DOMAIN, specifier)).isEmpty();
  }

  @Test
  void aFileOutsideTheSelectionIsNotReported() {
    assertThat(matches("express", OTHER, "static:express")).isEmpty();
  }

  @Test
  void projectFilesAndExternalNamesOfOneSelectorAreBothReported() {
    final ArchitectureSelector target =
        new ArchitectureSelector(List.of(), List.of("web"), List.of(), List.of("express"));
    final ArchitectureRule rule =
        ArchitectureFixtures.rule(
            "no-framework",
            ArchitectureFixtures.dirs(ArchitectureFixtures.DOMAIN_DIR),
            Kind.MUST_NOT_DEPEND_ON,
            target
        );

    final List<GraphMatch> matches =
        GraphRules.compile(
            ArchitectureFixtures.definition(DependencyArchitectureRule.ID, List.of(rule))
        )
            .matches(
                new DependencyGraph(List.of(ArchitectureFixtures.edge(DOMAIN, OTHER))),
                project(DOMAIN, "static:express")
            );

    assertThat(matches).hasSize(2);
    assertThat(ArchitectureRuleBody.decode(ArchitectureRuleBody.encode(List.of(rule))))
        .containsExactly(rule);
  }

  @Test
  void aSelectorOfExternalNamesSelectsNoFile() {
    assertThat(external("express").matcher().test(DOMAIN)).isFalse();
  }

  @Test
  void externalIsRefusedOutsideMustNotDependOnAndWithAWildcard() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                ArchitectureFixtures.rule(
                    "r",
                    ArchitectureFixtures.dirs(ArchitectureFixtures.DOMAIN_DIR),
                    Kind.MAY_ONLY_DEPEND_ON,
                    external("express")
                )
        )
        .withMessageContaining("must-not-depend-on only");
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                ArchitectureFixtures.rule(
                    "r",
                    external("express"),
                    Kind.MUST_NOT_DEPEND_ON,
                    ArchitectureFixtures.dirs(ArchitectureFixtures.DOMAIN_DIR)
                )
        )
        .withMessageContaining("select names project files");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> external("org.springframework.*"))
        .withMessageContaining("no wildcard");
  }

  private static List<GraphMatch> matches(
      final String entry,
      final String file,
      final String... specifiers
  ) {
    final ArchitectureRule rule =
        ArchitectureFixtures.rule(
            "no-framework",
            ArchitectureFixtures.dirs(ArchitectureFixtures.DOMAIN_DIR),
            Kind.MUST_NOT_DEPEND_ON,
            external(entry)
        );
    return GraphRules.compile(
        ArchitectureFixtures.definition(DependencyArchitectureRule.ID, List.of(rule))
    )
        .matches(new DependencyGraph(List.of()), project(file, specifiers));
  }

  private static ProjectFiles project(final String file, final String... specifiers) {
    return new ProjectFiles(
        Set.of(file),
        Set.of(),
        Map.of(),
        Map.of(
            file,
            Arrays.stream(specifiers).map(specifier -> new Reference(specifier, SPAN)).toList()
        )
    );
  }

  private static ArchitectureSelector external(final String... names) {
    return new ArchitectureSelector(List.of(), List.of(), List.of(), List.of(names));
  }
}
