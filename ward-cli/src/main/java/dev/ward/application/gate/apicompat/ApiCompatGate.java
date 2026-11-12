package dev.ward.application.gate.apicompat;

import dev.ward.analysis.domain.api.ApiSurfaceException;
import dev.ward.analysis.domain.api.ApiType;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.domain.apicompat.ApiChange;
import dev.ward.domain.apicompat.ApiComparison;
import dev.ward.domain.config.ApiCompatOptions;
import dev.ward.domain.gate.Finding;
import dev.ward.domain.gate.FindingSeverity;
import dev.ward.domain.gate.GateFindings;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import dev.ward.domain.gate.QualityGate;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The {@code api-compat} gate: compares the public API of the main sources in the working tree with
 * the API of the same sources at a baseline Git revision. It runs no build and no external tool.
 */
final class ApiCompatGate implements QualityGate {
  private static final Logger LOGGER = Logger.getLogger(ApiCompatGate.class.getName());
  private static final String RULE_PREFIX = "api-compat:";
  private static final String TOOL = "ward";
  private static final String POM = "pom.xml";
  private static final List<String> GRADLE_SCRIPTS = List.of("build.gradle.kts", "build.gradle");
  private static final String JAVA = ".java";

  private final String name;
  private final ApiCompatOptions options;
  private final ApiCompatPorts ports;

  ApiCompatGate(final String name, final ApiCompatOptions options, final ApiCompatPorts ports) {
    this.name = name;
    this.options = options;
    this.ports = ports;
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public GateResult execute(final Project project) {
    try {
      return compare(project.root());
    } catch (ApiCompatException unusable) {
      LOGGER.log(Level.INFO, "event=api-compat-unusable gate={0}", name);
      return GateResult.builder(name, GateStatus.EXECUTION_ERROR)
          .summary(unusable.getMessage())
          .stderr(unusable.getMessage())
          .build();
    }
  }

  private GateResult compare(final Path root) {
    final Baseline baseline = new BaselineResolver(ports.revisions()).resolve(root, options.base());
    final List<String> baselineRoots = baselineRoots(root, baseline);
    final List<SourceUnit> before = baselineUnits(root, baseline, baselineRoots);
    if (before.isEmpty()) {
      throw new ApiCompatException(
          "The baseline " + baseline.ref() + " has no sources for this project.");
    }
    final List<SourceUnit> after = workingUnits(root, workingRoots(root));
    final List<ApiType> baselineApi = surface(root, before, baseline.ref());
    final List<ApiType> currentApi = surface(root, after, "the working tree");
    requireIncludesMatch(baselineApi);
    LOGGER.log(
        Level.INFO,
        "event=api-compat-types baseline={0} working={1}",
        new Object[] {baselineApi.size(), currentApi.size()});
    final List<ApiChange> changes = ApiComparison.compare(baselineApi, currentApi, this::included);
    logCounts(changes);
    return result(baseline, baselineApi, changes);
  }

  private List<String> baselineRoots(final Path root, final Baseline baseline) {
    return ports
        .layout()
        .of(
            ports.revisions().read(root, baseline.commit(), POM),
            firstPresent(script -> ports.revisions().read(root, baseline.commit(), script)));
  }

  private List<String> workingRoots(final Path root) {
    return ports
        .layout()
        .of(
            ports.working().read(root, POM),
            firstPresent(script -> ports.working().read(root, script)));
  }

  private static Optional<String> firstPresent(final Function<String, Optional<String>> read) {
    return GRADLE_SCRIPTS.stream().map(read).flatMap(Optional::stream).findFirst();
  }

  private List<SourceUnit> baselineUnits(
      final Path root, final Baseline baseline, final List<String> roots) {
    final Map<String, String> sources = new TreeMap<>();
    for (final String directory : roots) {
      for (final String file : ports.revisions().files(root, baseline.commit(), directory)) {
        if (file.endsWith(JAVA)) {
          final String path = directory + "/" + file;
          sources.put(
              path,
              ports
                  .revisions()
                  .read(root, baseline.commit(), path)
                  .orElseThrow(() -> unreadable(path, baseline.ref())));
        }
      }
    }
    return units(sources);
  }

  private List<SourceUnit> workingUnits(final Path root, final List<String> roots) {
    final Map<String, String> sources = new TreeMap<>();
    for (final String directory : roots) {
      for (final String file : ports.working().files(root, directory)) {
        if (file.endsWith(JAVA)) {
          final String path = directory + "/" + file;
          sources.put(
              path,
              ports
                  .working()
                  .read(root, path)
                  .orElseThrow(() -> unreadable(path, "the working tree")));
        }
      }
    }
    return units(sources);
  }

  private static ApiCompatException unreadable(final String path, final String revision) {
    return new ApiCompatException("Cannot read " + path + " at " + revision + ".");
  }

  private static List<SourceUnit> units(final Map<String, String> sources) {
    final List<SourceUnit> units = new ArrayList<>();
    sources.forEach((path, text) -> units.add(new SourceUnit(path, AnalysisLanguage.JAVA, text)));
    return units;
  }

  private List<ApiType> surface(final Path root, final List<SourceUnit> units, final String where) {
    try {
      return ports.surfaces().read(root, units);
    } catch (ApiSurfaceException broken) {
      throw new ApiCompatException(
          "Cannot read the API of " + broken.path() + " at " + where + ": " + broken.getMessage());
    }
  }

  private boolean included(final String typeName) {
    return options.include().isEmpty()
        || options.include().stream().anyMatch(prefix -> matches(prefix, typeName));
  }

  private static boolean matches(final String prefix, final String typeName) {
    return typeName.equals(prefix) || typeName.startsWith(prefix + ".");
  }

  private void requireIncludesMatch(final List<ApiType> baselineApi) {
    for (final String prefix : options.include()) {
      if (baselineApi.stream().noneMatch(type -> matches(prefix, type.name()))) {
        throw new ApiCompatException(
            "The include prefix '"
                + prefix
                + "' of api-compat matches no type at the baseline; check the package name.");
      }
    }
  }

  private void logCounts(final List<ApiChange> changes) {
    final Map<String, Integer> counts = new TreeMap<>();
    changes.forEach(change -> counts.merge(change.kind(), 1, Integer::sum));
    LOGGER.log(
        Level.INFO,
        "event=api-compat-findings total={0} byKind={1}",
        new Object[] {changes.size(), counts});
  }

  private GateResult result(
      final Baseline baseline, final List<ApiType> baselineApi, final List<ApiChange> changes) {
    final List<Finding> findings = changes.stream().map(this::finding).toList();
    final long compared =
        baselineApi.stream().filter(type -> included(type.name()) && type.isVisible()).count();
    final GateStatus status = findings.isEmpty() ? GateStatus.PASSED : GateStatus.FAILED;
    final String summary =
        findings.isEmpty()
            ? "No incompatible change to " + compared + " API types since " + baseline.ref()
            : findings.size() + " incompatible API change(s) since " + baseline.ref();
    return GateResult.builder(name, status)
        .summary(summary)
        .errors(findings.size())
        .warnings(0)
        .build()
        .withFindings(GateFindings.of(findings));
  }

  private Finding finding(final ApiChange change) {
    return new Finding(
        name,
        TOOL,
        RULE_PREFIX + change.kind(),
        FindingSeverity.ERROR,
        change.message(),
        change.location().path(),
        change.location().line(),
        change.location().column(),
        null,
        List.of());
  }
}
