package dev.ward.application.gate;

import dev.ward.application.license.LicenseService;
import dev.ward.application.license.PackageLicense;
import dev.ward.domain.config.LicenseSettings;
import dev.ward.domain.config.WardException;
import dev.ward.domain.dependency.Dependency;
import dev.ward.domain.gate.Finding;
import dev.ward.domain.gate.FindingSeverity;
import dev.ward.domain.gate.GateFindings;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import dev.ward.domain.gate.QualityGate;
import dev.ward.domain.license.LicensePolicy;
import dev.ward.domain.license.LicenseVerdict;
import dev.ward.domain.project.Project;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The native {@code license} gate: checks the license of every package of the dependency inventory
 * against the policy of {@code [checks.license]}. A license Ward cannot find or recognize is its
 * own finding, never an allowed one. An unusable inventory is an execution error.
 */
public final class LicenseGate implements QualityGate {
  /** The finding of a license that the deny policy lists. */
  public static final String DENIED = "ward:license/denied";

  /** The finding of a license that the allow policy does not list. */
  public static final String NOT_ALLOWED = "ward:license/not-allowed";

  /** The finding of a license that is missing or unrecognized. */
  public static final String UNKNOWN = "ward:license/unknown";

  private static final Logger LOGGER = Logger.getLogger(LicenseGate.class.getName());
  private static final String TOOL = "ward";

  private final String name;
  private final LicenseService service;
  private final LicenseSettings settings;
  private final LicensePolicy policy;

  /** Creates the gate called {@code name} that applies {@code policy} through {@code service}. */
  public LicenseGate(
      final String name, final LicenseService service, final LicenseSettings settings) {
    this.name = name;
    this.service = service;
    this.settings = settings;
    this.policy = new LicensePolicy(settings);
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public GateResult execute(final Project project) {
    LOGGER.log(Level.FINE, () -> "event=license-policy mode=" + settings.mode());
    final List<PackageLicense> licenses;
    try {
      licenses = service.licenses(project, settings.scopes());
    } catch (WardException exception) {
      return GateResult.builder(name, GateStatus.EXECUTION_ERROR)
          .summary(exception.getMessage())
          .build();
    }
    final List<Finding> findings = new ArrayList<>();
    for (final PackageLicense license : licenses) {
      findings(license).ifPresent(findings::add);
    }
    final int errors = count(findings, FindingSeverity.ERROR);
    final int warnings = count(findings, FindingSeverity.WARNING);
    return GateResult.builder(name, errors > 0 ? GateStatus.FAILED : GateStatus.PASSED)
        .summary(licenses.size() + " package(s), " + findings.size() + " finding(s)")
        .errors(errors)
        .warnings(warnings)
        .build()
        .withFindings(GateFindings.of(findings));
  }

  private Optional<Finding> findings(final PackageLicense license) {
    if (!license.known()) {
      final FindingSeverity severity =
          settings.unknownFails() ? FindingSeverity.ERROR : FindingSeverity.WARNING;
      return Optional.of(
          finding(
              license, UNKNOWN, severity, "has an unknown license: " + license.unknownReason()));
    }
    final LicenseVerdict verdict = policy.check(license.expression().get());
    if (verdict.ok()) {
      return Optional.empty();
    }
    final boolean denied = verdict.outcome() == LicenseVerdict.Outcome.DENIED;
    final String message =
        "is licensed "
            + license.expression().get().render()
            + (license.declared().isEmpty() ? "" : " (declared: " + license.declared() + ")")
            + (denied ? ", which the policy denies" : ", which the policy does not allow")
            + " ["
            + verdict.entry()
            + "]";
    return Optional.of(
        finding(license, denied ? DENIED : NOT_ALLOWED, FindingSeverity.ERROR, message));
  }

  private Finding finding(
      final PackageLicense license,
      final String rule,
      final FindingSeverity severity,
      final String message) {
    final Dependency dependency = license.dependency();
    final String file =
        dependency.declaredIn().isEmpty() ? dependency.source() : dependency.declaredIn();
    final String subject =
        dependency.id().ecosystem().purlType()
            + " "
            + dependency.id().name()
            + ":"
            + dependency.id().version();
    return new Finding(
        name, TOOL, rule, severity, subject + " " + message, file, null, null, null, List.of());
  }

  private static int count(final List<Finding> findings, final FindingSeverity severity) {
    return (int) findings.stream().filter(finding -> finding.severity() == severity).count();
  }
}
