package dev.ward.application.gate.apicompat;

import dev.ward.analysis.domain.api.ApiSurfaceException;
import dev.ward.analysis.domain.api.ApiType;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.domain.apicompat.ApiChange;
import dev.ward.domain.apicompat.ApiComparison;
import dev.ward.domain.apicompat.ApiModules;
import dev.ward.domain.config.ApiCompatOptions;
import dev.ward.domain.gate.Finding;
import dev.ward.domain.gate.FindingSeverity;
import dev.ward.domain.gate.GateFindings;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import dev.ward.domain.gate.QualityGate;
import dev.ward.domain.gate.RevisionReadException;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * The {@code api-compat} gate: compares the public API of the main sources in the working tree with
 * the API of the same sources at a baseline Git revision. It runs no build and no external tool.
 */
final class ApiCompatGate implements QualityGate {
  private static final Logger LOGGER = Logger.getLogger(ApiCompatGate.class.getName());
  private static final String RULE_PREFIX = "api-compat:";
  private static final String TOOL = "ward";

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
    } catch (ApiCompatException | RevisionReadException unusable) {
      if (LOGGER.isLoggable(Level.INFO)) {
        LOGGER.info("event=api-compat-unusable gate=" + name);
      }
      return GateResult.builder(name, GateStatus.EXECUTION_ERROR)
          .summary(unusable.getMessage())
          .stderr(unusable.getMessage())
          .build();
    }
  }

  private GateResult compare(final Path root) {
    final Baseline baseline = new BaselineResolver(ports.revisions()).resolve(root, options.base());
    final ApiSources sources = new ApiSources(ports, root);
    final ApiSources.Sources before = sources.atBaseline(baseline);
    if (before.units().isEmpty()) {
      throw new ApiCompatException(
          "The baseline " + baseline.ref() + " has no sources for this project.");
    }
    final ApiSources.Sources now = sources.inWorkingTree();
    final List<ApiType> baselineApi = surface(root, before.units(), baseline.ref());
    final List<ApiType> currentApi = surface(root, now.units(), "the working tree");
    requireIncludesMatch(baselineApi);
    requireModulesExist(before.modules(), now.modules());
    final Predicate<String> scope = scope(baselineApi, before.modules());
    if (LOGGER.isLoggable(Level.INFO)) {
      LOGGER.info(
          "event=api-compat-types baseline="
              + baselineApi.size()
              + " working="
              + currentApi.size()
              + " modules="
              + before.modules().size());
    }
    final List<ApiChange> changes =
        new ArrayList<>(ApiComparison.compare(baselineApi, currentApi, scope));
    changes.addAll(
        ApiModules.moved(baselineApi, currentApi, before.modules(), now.modules(), scope));
    final List<String> modules = new ArrayList<>(before.modules());
    modules.addAll(now.modules());
    logCounts(changes);
    return result(baseline, baselineApi, scope, modules, changes);
  }

  /** Limits the API to the {@code include} prefixes and the {@code modules} of the options. */
  private Predicate<String> scope(final List<ApiType> baselineApi, final List<String> modules) {
    final Set<String> named =
        baselineApi.stream()
            .filter(type -> inNamedModule(ApiModules.owner(type.location().path(), modules)))
            .map(ApiType::name)
            .collect(Collectors.toSet());
    return name -> included(name) && (options.modules().isEmpty() || named.contains(name));
  }

  private boolean inNamedModule(final String module) {
    return options.modules().isEmpty() || options.modules().contains(module);
  }

  private void requireModulesExist(final List<String> baseline, final List<String> current) {
    for (final String module : options.modules()) {
      if (!baseline.contains(module) && !current.contains(module)) {
        throw new ApiCompatException(
            "The module '"
                + module
                + "' of api-compat is not a module of the build at the baseline or in the"
                + " working tree; check the directory.");
      }
    }
  }

  private List<ApiType> surface(final Path root, final List<SourceUnit> units, final String where) {
    try {
      return ports.surfaces().read(root, units);
    } catch (ApiSurfaceException broken) {
      throw new ApiCompatException(
          "Cannot read the API of " + broken.path() + " at " + where + ": " + broken.getMessage(),
          broken);
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

  private static void logCounts(final List<ApiChange> changes) {
    if (LOGGER.isLoggable(Level.INFO)) {
      final Map<String, Integer> counts = new TreeMap<>();
      changes.forEach(change -> counts.merge(change.kind(), 1, Integer::sum));
      LOGGER.info("event=api-compat-findings total=" + changes.size() + " byKind=" + counts);
    }
  }

  private GateResult result(
      final Baseline baseline,
      final List<ApiType> baselineApi,
      final Predicate<String> scope,
      final List<String> modules,
      final List<ApiChange> changes) {
    final List<Finding> findings =
        changes.stream().map(change -> finding(change, modules)).toList();
    final long compared =
        baselineApi.stream().filter(type -> scope.test(type.name()) && type.isVisible()).count();
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

  private Finding finding(final ApiChange change, final List<String> modules) {
    final String module = ApiModules.owner(change.location().path(), modules);
    return new Finding(
        name,
        TOOL,
        RULE_PREFIX + change.kind(),
        FindingSeverity.ERROR,
        module.isEmpty() ? change.message() : "[" + module + "] " + change.message(),
        change.location().path(),
        change.location().line(),
        change.location().column(),
        null,
        List.of());
  }
}
