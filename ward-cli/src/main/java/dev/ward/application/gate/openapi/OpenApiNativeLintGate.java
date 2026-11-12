package dev.ward.application.gate.openapi;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.LoadError;
import dev.ward.analysis.domain.openapi.LoadResult;
import dev.ward.analysis.domain.openapi.OpenApiDocument;
import dev.ward.analysis.domain.openapi.OpenApiDocuments;
import dev.ward.application.gate.AnalysisGate;
import dev.ward.application.gate.AnalysisPorts;
import dev.ward.application.gate.OpenApiRuleSetups;
import dev.ward.application.gate.RuleSetup;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import dev.ward.domain.gate.ProjectFiles;
import dev.ward.domain.gate.QualityGate;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Lints the committed OpenAPI spec with the native {@code ward:openapi/*} rules: no executable, no
 * tool on the path. It loads the spec and the files its references name, refuses an inline {@code
 * x-lint-ignore}, and runs the rules through the analysis engine. A spec or reference that cannot
 * be used is an execution error, never a pass; an error or a warning fails the gate, a note does
 * not.
 */
public final class OpenApiNativeLintGate implements QualityGate {
  static final String INLINE_IGNORE = "x-lint-ignore";
  private static final Logger LOGGER = Logger.getLogger(OpenApiNativeLintGate.class.getName());

  private final String name;
  private final String spec;
  private final String rulesFile;
  private final AnalysisPorts ports;
  private final ProjectFiles files;

  /**
   * Creates the gate.
   *
   * @param name the gate's name
   * @param spec the project-relative path of the OpenAPI file
   * @param rulesFile the {@code rules-file} path, or {@code null} for {@code ward-rules.toml}
   * @param ports the analysis adapters; their source reader is not used
   * @param files reads the spec and the files it references
   */
  public OpenApiNativeLintGate(
      final String name,
      final String spec,
      final String rulesFile,
      final AnalysisPorts ports,
      final ProjectFiles files) {
    this.name = name;
    this.spec = spec;
    this.rulesFile = rulesFile;
    this.ports = ports;
    this.files = files;
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public GateResult execute(final Project project) {
    final Path root = project.root();
    if (!files.exists(root, spec)) {
      LOGGER.log(Level.INFO, () -> "event=openapi-lint-refused gate=" + name + " reason=no-spec");
      return OpenApiResults.stopped(name, GateStatus.FAILED, OpenApiResults.missingSpec(spec));
    }
    final LoadResult loaded = OpenApiDocuments.load(spec, path -> files.read(root, path));
    if (!loaded.loaded()) {
      return loadError(loaded.errors());
    }
    final OpenApiDocument document = loaded.document().orElseThrow();
    final Optional<String> ignored = inlineIgnore(document);
    if (ignored.isPresent()) {
      LOGGER.log(Level.INFO, () -> "event=openapi-lint-refused gate=" + name + " reason=ignore");
      return OpenApiResults.stopped(
          name,
          GateStatus.FAILED,
          "Inline lint ignores (" + INLINE_IGNORE + ") are not allowed in " + ignored.get() + ".");
    }
    return lint(project, document);
  }

  private GateResult lint(final Project project, final OpenApiDocument document) {
    final List<SourceUnit> units = units(project.root(), document);
    final AnalysisPorts own =
        new AnalysisPorts(
            ports.rules(),
            ports.matcher(),
            (root, languages) -> units,
            ports.frontends(),
            ports.caches(),
            ports.codec(),
            ports.engineVersion(),
            ports.dependencies(),
            ports.nativeTypeFacts());
    final RuleSetup setup = OpenApiRuleSetups.create(name, rulesFile, own);
    final int rules = setup.resolve(project.root(), 0).rules().size();
    LOGGER.log(
        Level.INFO,
        () ->
            "event=openapi-lint-start gate=" + name + " files=" + units.size() + " rules=" + rules);
    // One check, one line: the per-category lines are for the ward gate only.
    final GateResult result =
        new AnalysisGate(name, setup, own, null, List.of())
            .execute(project)
            .withSections(List.of());
    final GateResult policed = failingOnWarnings(result);
    LOGGER.log(
        Level.INFO, () -> "event=openapi-lint-done gate=" + name + " status=" + policed.status());
    return policed;
  }

  private GateResult loadError(final List<LoadError> errors) {
    LOGGER.log(
        Level.INFO,
        () -> "event=openapi-lint-load-error gate=" + name + " problems=" + errors.size());
    return GateResult.builder(name, GateStatus.EXECUTION_ERROR)
        .summary(errors.getFirst().describe())
        .stderr(String.join("\n", errors.stream().map(LoadError::describe).toList()))
        .build();
  }

  /** The gate fails on error and warning findings, like {@code -n warn}; notes are reported. */
  private static GateResult failingOnWarnings(final GateResult result) {
    if (result.status() != GateStatus.PASSED
        || result.warnings() == null
        || result.warnings() == 0) {
      return result;
    }
    return new GateResult(
        result.name(),
        result.type(),
        GateStatus.FAILED,
        result.command(),
        result.exitCode(),
        result.duration(),
        result.stdout(),
        result.stderr(),
        result.summary(),
        result.errors(),
        result.warnings(),
        result.findings(),
        result.scope(),
        result.prerequisite(),
        List.of());
  }

  /** The first loaded file that has the {@code x-lint-ignore} key as an object key. */
  private static Optional<String> inlineIgnore(final OpenApiDocument document) {
    for (final DocNode root : document.files().values()) {
      final Optional<String> hit = findIgnore(root);
      if (hit.isPresent()) {
        return hit;
      }
    }
    return Optional.empty();
  }

  private static Optional<String> findIgnore(final DocNode root) {
    final List<DocNode> stack = new ArrayList<>(List.of(root));
    while (!stack.isEmpty()) {
      final DocNode node = stack.removeLast();
      if (node.get(INLINE_IGNORE).isPresent()) {
        return Optional.of(node.file());
      }
      stack.addAll(node.children());
    }
    return Optional.empty();
  }

  private List<SourceUnit> units(final Path root, final OpenApiDocument document) {
    final List<SourceUnit> units = new ArrayList<>();
    for (final String path : document.files().keySet()) {
      files
          .read(root, path)
          .ifPresent(text -> units.add(new SourceUnit(path, AnalysisLanguage.OPENAPI, text)));
    }
    return List.copyOf(units);
  }
}
