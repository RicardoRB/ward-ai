package dev.ward.application.buildpolicy;

import dev.ward.application.dependency.DependencyInventoryService;
import dev.ward.domain.buildpolicy.BuildFiles;
import dev.ward.domain.buildpolicy.BuildModel;
import dev.ward.domain.buildpolicy.BuildPolicyFinding;
import dev.ward.domain.buildpolicy.BuildPolicyInput;
import dev.ward.domain.buildpolicy.BuildPolicyRules;
import dev.ward.domain.buildpolicy.BuildRule;
import dev.ward.domain.gate.Finding;
import dev.ward.domain.gate.FindingSeverity;
import dev.ward.domain.project.Project;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import javax.inject.Inject;

/**
 * Reads the build files of a project and runs the selected build rules over them. A file that
 * cannot be read, or an inventory that cannot be resolved, ends the run with an exception; it is
 * never an empty result.
 */
public final class BuildPolicyService {
  private static final Logger LOGGER = Logger.getLogger(BuildPolicyService.class.getName());
  private static final String TOOL = "build-policy";

  private final BuildFiles files;
  private final DependencyInventoryService inventory;

  /** Creates the service over the build files and the dependency inventory. */
  @Inject
  public BuildPolicyService(final BuildFiles files, final DependencyInventoryService inventory) {
    this.files = files;
    this.inventory = inventory;
  }

  /**
   * Runs {@code settings} over {@code project} and returns the findings of the gate {@code gate}.
   *
   * @throws dev.ward.domain.buildpolicy.BuildFileException if a build file is unusable
   * @throws dev.ward.domain.dependency.DependencyInventoryException if convergence needs an
   *     inventory that cannot be resolved
   */
  public List<Finding> run(
      final Project project, final String gate, final List<BuildPolicySetting> settings) {
    LOGGER.log(Level.FINE, () -> "event=build-policy-start rules=" + settings.size());
    final BuildModel model = files.read(project.root());
    LOGGER.log(
        Level.FINE,
        () ->
            "event=build-policy-read declarations="
                + model.declarations().size()
                + " tools="
                + model.tools().size());
    final List<Finding> findings =
        settings.stream().flatMap(setting -> check(project, gate, model, setting)).toList();
    LOGGER.log(Level.FINE, () -> "event=build-policy-done findings=" + findings.size());
    return findings;
  }

  private Stream<Finding> check(
      final Project project,
      final String gate,
      final BuildModel model,
      final BuildPolicySetting setting) {
    final BuildPolicyInput input =
        new BuildPolicyInput(model, () -> inventory.inventory(project), setting.options());
    return BuildPolicyRules.forRule(setting.rule()).check(input).stream()
        .map(found -> toFinding(gate, setting, found));
  }

  private static Finding toFinding(
      final String gate, final BuildPolicySetting setting, final BuildPolicyFinding found) {
    final BuildRule rule = found.rule();
    return new Finding(
        gate,
        TOOL,
        rule.id(),
        setting.warning() ? FindingSeverity.WARNING : FindingSeverity.ERROR,
        found.message(),
        found.file(),
        found.line() > 0 ? found.line() : null,
        null,
        null,
        List.of());
  }
}
