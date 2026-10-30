package dev.ward.infrastructure.cli.init;

import dev.ward.application.init.InitSetupCatalog;
import dev.ward.application.init.PreviousChoices;
import dev.ward.domain.config.WardException;
import dev.ward.domain.init.ArchitectureGates;
import dev.ward.domain.init.InitSelection;
import dev.ward.domain.init.PresetGateOrder;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Resolves every init question the same way: the option that answers it, otherwise the default when
 * {@code --yes} is given, otherwise an answer read by the wizard.
 */
final class InitQuestions {
  static final String DEFAULT_ARCHITECTURE = "layered";
  static final String GATES_MISSING =
      "No gate selection given. In non-interactive mode pass --preset or --gate, or --yes to"
          + " accept defaults.";
  private static final int STANDARD_CHOICE = 1;
  // Without strict, custom moves up to the second choice.

  private final Answers answers;
  private final InitWizard wizard;
  private final InitConfirmations confirmations;
  private final InitSetupCatalog catalog;
  private final Project project;
  private final boolean useDefaults;
  private final QuestionProgress progress;
  private Optional<PreviousChoices> previous = Optional.empty();

  /**
   * Answers are read only on a terminal, or when no setup option was given (piped answers).
   * Automation that passes setup options is never asked, so an open but silent input cannot block.
   */
  InitQuestions(
      final Answers answers,
      final InitPrompts prompts,
      final InitSetupCatalog catalog,
      final Project project,
      final boolean terminal) {
    this.answers = answers;
    this.progress = prompts.progress();
    this.wizard = prompts.wizard();
    this.confirmations = prompts.confirmations();
    this.catalog = catalog;
    this.project = project;
    this.useDefaults = answers.yes() || !terminal && answers.setupGiven();
  }

  /** Whether an existing configuration may be overwritten after asking. */
  boolean confirmOverwrite(final Path file) {
    return !useDefaults && confirmations.overwrite(file);
  }

  /** Pre-fills the preset default and gate selection from the configuration being replaced. */
  void reconfigure(final PreviousChoices choices) {
    previous = Optional.of(choices);
  }

  InitSelection resolve() {
    final boolean gatesGiven =
        answers.preset() != null || answers.gates() != null && !answers.gates().isEmpty();
    progress.expect(questionsToAsk(gatesGiven));
    final QualityPreset preset = preset(gatesGiven);
    final List<String> integrations = integrations();
    final List<String> gates = gates(gatesGiven, preset);
    // A preset given as an option or by default keeps its architecture default; only chosen gates
    // prompt for a style.
    final List<String> chosenGates =
        gatesGiven
            ? Objects.requireNonNullElse(answers.gates(), List.<String>of())
            : useDefaults ? List.of() : gates;
    final String architecture = architecture(chosenGates, gates);
    final ToolQuestions.Selection tools =
        new ToolQuestions(answers, wizard, confirmations, useDefaults, progress)
            .resolve(catalog, project, gates);
    final boolean qualityHooks =
        answers.qualityHooks() != null ? answers.qualityHooks() : askQualityHooks();
    return new InitSelection(
        integrations,
        gates,
        architecture,
        preset,
        tools.tools(),
        tools.validations(),
        tools.installs(),
        qualityHooks,
        answers.ci(),
        answers.agentRules());
  }

  private QualityPreset preset(final boolean gatesGiven) {
    if (gatesGiven) {
      return answers.preset() == null ? null : explicitPreset(answers.preset());
    }
    if (answers.yes()) {
      return QualityPreset.STANDARD;
    }
    if (useDefaults) {
      throw new WardException(GATES_MISSING);
    }
    progress.step();
    if (!catalog.presets(project).contains(QualityPreset.STRICT)) {
      return wizard.selectPreset(STANDARD_CHOICE, false);
    }
    return wizard.selectPreset(STANDARD_CHOICE, true);
  }

  /** Returns the preset named on the command line, refused when its toolchain does not offer it. */
  private QualityPreset explicitPreset(final String id) {
    final QualityPreset preset = catalog.preset(id);
    final List<QualityPreset> offered = catalog.presets(project);
    if (!offered.contains(preset)) {
      throw new WardException(
          preset.id()
              + " is not available yet for "
              + project.buildTool().displayName()
              + " projects; use --preset "
              + offered.getFirst().id()
              + ".");
    }
    return preset;
  }

  private List<String> integrations() {
    if (answers.integrations() != null && !answers.integrations().isEmpty()) {
      return answers.integrations();
    }
    if (useDefaults) {
      return List.of(InitSetupCatalog.NO_INTEGRATION);
    }
    progress.step();
    return wizard.selectIntegrations(catalog);
  }

  private List<String> gates(final boolean gatesGiven, final QualityPreset preset) {
    if (gatesGiven) {
      final List<String> explicit = Objects.requireNonNullElse(answers.gates(), List.<String>of());
      return preset == null
          ? explicit
          : PresetGateOrder.withExplicitGates(catalog.presetGates(project, preset), explicit);
    }
    if (answers.yes()) {
      return catalog.presetGates(project, preset);
    }
    progress.step();
    return wizard.selectGates(
        catalog,
        project,
        preset == null
            ? previous.map(PreviousChoices::gates).orElse(List.of())
            : catalog.presetGates(project, preset));
  }

  private String architecture(final List<String> chosenGates, final List<String> gates) {
    if (Collections.disjoint(chosenGates, ArchitectureGates.IDS)) {
      return answers.architecture() == null
              && useDefaults
              && !Collections.disjoint(gates, ArchitectureGates.IDS)
          ? DEFAULT_ARCHITECTURE
          : answers.architecture();
    }
    final String selected;
    if (answers.architecture() != null) {
      selected = answers.architecture();
    } else if (useDefaults) {
      selected = DEFAULT_ARCHITECTURE;
    } else {
      progress.expect(1);
      progress.step();
      selected = wizard.selectArchitecture(catalog);
    }
    catalog.architecture(selected);
    return selected;
  }

  private boolean askQualityHooks() {
    if (useDefaults) {
      return false;
    }
    progress.step();
    return confirmations.qualityHooks();
  }

  /** The base questions this run will ask; follow-ups are added when they become certain. */
  private int questionsToAsk(final boolean gatesGiven) {
    if (useDefaults) {
      return 0;
    }
    int questions = gatesGiven ? 0 : 2;
    if (answers.integrations() == null || answers.integrations().isEmpty()) {
      questions++;
    }
    if (answers.tools() == null) {
      questions++;
    }
    if (answers.qualityHooks() == null) {
      questions++;
    }
    return questions;
  }

  /** The command-line answers; {@code null} means the option was not given. */
  record Answers(
      String preset,
      List<String> integrations,
      List<String> gates,
      String architecture,
      List<String> tools,
      List<String> validations,
      List<String> installTools,
      Boolean qualityHooks,
      String ci,
      boolean agentRules,
      boolean yes) {
    boolean setupGiven() {
      return preset != null || gates != null || integrations != null;
    }
  }
}
