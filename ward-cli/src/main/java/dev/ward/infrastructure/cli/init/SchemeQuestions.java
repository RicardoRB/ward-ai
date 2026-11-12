package dev.ward.infrastructure.cli.init;

import dev.ward.application.init.InitSetupCatalog;
import dev.ward.application.init.swift.XcodeSchemeChoices;
import dev.ward.domain.project.Project;
import java.util.List;

/** Resolves the Xcode scheme of a project that has several. */
final class SchemeQuestions {
  private final InitQuestions.Answers answers;
  private final InitWizard wizard;
  private final boolean useDefaults;

  SchemeQuestions(
      final InitQuestions.Answers answers, final InitWizard wizard, final boolean useDefaults) {
    this.answers = answers;
    this.wizard = wizard;
    this.useDefaults = useDefaults;
  }

  /**
   * Returns the Xcode scheme the user named, or asks for one when the project has several schemes
   * and the wizard is interactive. Null for projects with no Xcode scheme choice.
   */
  String scheme(final InitSetupCatalog catalog, final Project project) {
    if (answers.scheme() != null) {
      return answers.scheme();
    }
    final List<String> schemes = XcodeSchemeChoices.of(catalog, project);
    if (schemes.size() < 2 || useDefaults) {
      return null;
    }
    return wizard.selectScheme(schemes);
  }
}
