package dev.ward.application.gate.openapi;

import dev.ward.analysis.domain.openapi.LoadError;
import dev.ward.analysis.domain.openapi.LoadResult;
import dev.ward.analysis.domain.openapi.OpenApiDocument;
import dev.ward.analysis.domain.openapi.OpenApiDocuments;
import dev.ward.analysis.domain.openapi.breaking.BreakingFinding;
import dev.ward.analysis.domain.openapi.breaking.BreakingRules;
import dev.ward.analysis.domain.openapi.breaking.DocumentPair;
import dev.ward.domain.gate.Finding;
import dev.ward.domain.gate.FindingSeverity;
import dev.ward.domain.gate.GateFindings;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import dev.ward.domain.gate.QualityGate;
import dev.ward.domain.project.Project;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reports breaking changes between the working-tree spec and the spec at a baseline commit with the
 * native {@code ward:openapi/*} breaking-change rules: no executable, no configuration file. A spec
 * or reference that cannot be read or parsed, at the baseline or in the working tree, is an
 * execution error, never a pass.
 */
final class OpenApiDiffGate implements QualityGate {
  private static final Logger LOGGER = Logger.getLogger(OpenApiDiffGate.class.getName());
  private static final String TOOL = "ward";

  private final String name;
  private final OpenApiGates.Ports ports;
  private final String spec;
  private final Function<Path, OpenApiBaseline> baseline;
  private final Clock clock;

  OpenApiDiffGate(
      final String name,
      final OpenApiGates.Ports ports,
      final String spec,
      final Function<Path, OpenApiBaseline> baseline,
      final Clock clock) {
    this.name = name;
    this.ports = ports;
    this.spec = spec;
    this.baseline = baseline;
    this.clock = clock;
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public GateResult execute(final Project project) {
    final Path root = project.root();
    if (!ports.files().exists(root, spec)) {
      LOGGER.log(Level.INFO, () -> "event=openapi-diff-refused gate=" + name + " reason=no-spec");
      return OpenApiResults.stopped(name, GateStatus.FAILED, OpenApiResults.missingSpec(spec));
    }
    final OpenApiBaseline resolved = baseline.apply(root);
    if (!resolved.found()) {
      LOGGER.log(
          Level.INFO,
          () -> "event=openapi-baseline gate=" + name + " status=none reason=" + resolved.reason());
      return OpenApiResults.stopped(name, resolved.status(), resolved.summary());
    }
    if (!ports.revisions().exists(root, resolved.commit(), spec)) {
      LOGGER.log(
          Level.INFO, () -> "event=openapi-baseline gate=" + name + " status=none reason=new-spec");
      return OpenApiResults.stopped(
          name,
          GateStatus.PASSED,
          "No baseline: " + spec + " does not exist at " + resolved.commit() + ", so it is new.");
    }
    LOGGER.log(
        Level.INFO,
        () ->
            "event=openapi-baseline gate=" + name + " status=found revision=" + resolved.commit());
    return compare(root, resolved.commit());
  }

  private GateResult compare(final Path root, final String commit) {
    final LoadResult before;
    try {
      before = OpenApiDocuments.load(spec, new RevisionFileReader(ports.revisions(), root, commit));
    } catch (final UncheckedIOException unreadable) {
      LOGGER.log(Level.INFO, () -> "event=openapi-diff-git-error gate=" + name);
      return error("Cannot read " + spec + " at " + commit + " from Git.");
    }
    final LoadResult after = OpenApiDocuments.load(spec, path -> ports.files().read(root, path));
    final Optional<GateResult> failed = loadFailure(before, "at " + commit);
    final Optional<GateResult> failedNow = loadFailure(after, "in the working tree");
    if (failed.isPresent() || failedNow.isPresent()) {
      return failed.orElseGet(failedNow::orElseThrow);
    }
    return report(before.document().orElseThrow(), after.document().orElseThrow());
  }

  private Optional<GateResult> loadFailure(final LoadResult result, final String where) {
    if (result.loaded()) {
      return Optional.empty();
    }
    LOGGER.log(
        Level.INFO,
        () -> "event=openapi-diff-load-error gate=" + name + " problems=" + result.errors().size());
    return Optional.of(
        GateResult.builder(name, GateStatus.EXECUTION_ERROR)
            .summary(result.errors().getFirst().describe() + " (" + where + ")")
            .stderr(String.join("\n", result.errors().stream().map(LoadError::describe).toList()))
            .build());
  }

  private GateResult error(final String summary) {
    return GateResult.builder(name, GateStatus.EXECUTION_ERROR).summary(summary).build();
  }

  private GateResult report(final OpenApiDocument base, final OpenApiDocument revision) {
    final List<BreakingFinding> breaking =
        BreakingRules.run(new DocumentPair(base, revision, LocalDate.now(clock)));
    LOGGER.log(
        Level.INFO, () -> "event=openapi-diff-done gate=" + name + " findings=" + breaking.size());
    final List<Finding> findings = breaking.stream().map(this::finding).toList();
    final GateStatus status = findings.isEmpty() ? GateStatus.PASSED : GateStatus.FAILED;
    final String summary =
        findings.isEmpty()
            ? "No breaking changes."
            : findings.size() + " breaking change" + (findings.size() == 1 ? "" : "s") + ".";
    return GateResult.builder(name, status)
        .summary(summary)
        .errors(findings.size())
        .build()
        .withFindings(GateFindings.of(findings));
  }

  private Finding finding(final BreakingFinding breaking) {
    return new Finding(
        name,
        TOOL,
        breaking.ruleId(),
        FindingSeverity.ERROR,
        breaking.message(),
        breaking.at().file(),
        breaking.at().anchor().startLine(),
        breaking.at().anchor().startColumn(),
        null,
        List.of());
  }
}
