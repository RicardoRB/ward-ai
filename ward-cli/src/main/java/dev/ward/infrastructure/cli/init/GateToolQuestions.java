package dev.ward.infrastructure.cli.init;

import dev.ward.application.init.InitSetupCatalog;
import dev.ward.application.init.swift.XcodeSchemeChoices;
import dev.ward.domain.config.WardException;
import dev.ward.domain.init.GateToolInstallCatalog;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.PlannedGateInstallation;
import dev.ward.domain.project.Project;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Resolves which missing gate executables the user consents to install. */
final class GateToolQuestions {
  private final InitQuestions.Answers answers;
  private final InitWizard wizard;
  private final InitConfirmations confirmations;
  private final boolean useDefaults;
  private final QuestionProgress progress;

  GateToolQuestions(
      final InitQuestions.Answers answers,
      final InitWizard wizard,
      final InitConfirmations confirmations,
      final boolean useDefaults,
      final QuestionProgress progress) {
    this.answers = answers;
    this.progress = progress;
    this.wizard = wizard;
    this.confirmations = confirmations;
    this.useDefaults = useDefaults;
  }

  /** The gates whose missing executables the user consented to install, validated. */
  List<String> resolve(
      final InitSetupCatalog catalog, final Project project, final List<String> gates) {
    final List<String> installs = installs(catalog, project, gates);
    validate(catalog, project, gates, installs);
    return installs;
  }

  private List<String> installs(
      final InitSetupCatalog catalog, final Project project, final List<String> gates) {
    if (answers.installTools() != null) {
      return List.copyOf(answers.installTools());
    }
    final List<String> selected = new ArrayList<>();
    if (!useDefaults) {
      gateInstalls(catalog, project, gates, selected);
    }
    return List.copyOf(selected);
  }

  private void gateInstalls(
      final InitSetupCatalog catalog,
      final Project project,
      final List<String> gates,
      final List<String> selected) {
    for (final String gateId : gates) {
      final InitGateOption gate = catalog.gate(project, gateId);
      final Optional<PlannedGateInstallation> plan = catalog.gateInstallation(project, gate);
      if (plan.isPresent() && plan.get().installable()) {
        progress.expect(1);
        progress.step();
        final GateToolInstallCatalog.InstallMethod method = installMethod(plan.get());
        if (confirmations.gateToolInstallation(plan.get().tool(), method)) {
          selected.add(gateId);
        }
      }
    }
  }

  private static GateToolInstallCatalog.InstallMethod installMethod(
      final PlannedGateInstallation plan) {
    return GateToolInstallCatalog.forExecutable(plan.tool()).orElseThrow().methods().stream()
        .filter(candidate -> candidate.manager().equals(plan.manager()))
        .findFirst()
        .orElseThrow();
  }

  private static void validate(
      final InitSetupCatalog catalog,
      final Project project,
      final List<String> gates,
      final List<String> installs) {
    installs.forEach(
        install -> {
          if (!gates.contains(install)) {
            throw new WardException(
                "--install-tool " + install + " requires --gate " + install + ".");
          }
          final Optional<PlannedGateInstallation> plan =
              catalog.gateInstallation(project, catalog.gate(project, install));
          if (catalog.gate(project, install).available()
              || plan.isEmpty()
              || !plan.orElseThrow().installable()) {
            throw new WardException("Gate '" + install + "' is not installable.");
          }
        });
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
