package dev.ward.application.init;

import dev.ward.domain.config.WardException;
import dev.ward.domain.gate.SupportedQualityGates;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.OwnedSection;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.node.PackageManifest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Renders the GitHub Actions workflow that runs the {@code ci} profile and uploads SARIF. */
public final class CiWorkflowTemplate {
  static final String GITHUB = "github";
  static final String DEVELOPMENT_VERSION = "dev";
  static final String BEGIN = "# ward-owned: github-ci v1 begin";
  static final String END = "# ward-owned: github-ci v1 end";
  private static final String WORKFLOWS = ".github/workflows/";
  private static final String SARIF = "ward.sarif";
  private static final String SEMGREP_VERSION = "1.181.0";
  public static final String STEP = "      - name: ";

  private CiWorkflowTemplate() {}

  /**
   * What the setup steps read from the project.
   *
   * @param manifest the Node package manifest, or {@link PackageManifest#EMPTY}
   * @param runtimeJdk the JDK that runs a JVM build, when init could read it
   */
  /** The facts a workflow step depends on: the manifest and the runtime JDK. */
  public record ProjectFacts(PackageManifest manifest, Optional<Integer> runtimeJdk) {}

  /**
   * Returns the workflow for {@code provider}, installing the tools the selected gates run.
   *
   * @throws WardException if {@code provider} is not supported
   */
  public static OwnedSection render(
      final String provider,
      final Project project,
      final List<InitGateOption> gates,
      final String wardVersion) {
    return render(provider, project, gates, wardVersion, PackageManifest.EMPTY);
  }

  /** Returns the workflow, reading the Node package manager's version from {@code manifest}. */
  public static OwnedSection render(
      final String provider,
      final Project project,
      final List<InitGateOption> gates,
      final String wardVersion,
      final PackageManifest manifest) {
    return render(
        provider, project, gates, wardVersion, new ProjectFacts(manifest, Optional.empty()));
  }

  /** Returns the workflow for the project's {@code facts}: its manifest and runtime JDK. */
  static OwnedSection render(
      final String provider,
      final Project project,
      final List<InitGateOption> gates,
      final String wardVersion,
      final ProjectFacts facts) {
    if (!GITHUB.equals(provider)) {
      throw new WardException(
          "Unknown CI provider '" + provider + "'. Supported providers: " + GITHUB);
    }
    final Set<String> ids = gates.stream().map(InitGateOption::id).collect(Collectors.toSet());
    final StringBuilder body = new StringBuilder(header(ids, project, fileName(project), facts));
    body.append(tools(ids));
    if (ids.contains(SupportedQualityGates.VULNERABILITIES)) {
      body.append(VulnerabilityCacheSteps.restore(project));
    }
    body.append(ward(wardVersion, sarif(project), category(project)));
    if (ids.contains(SupportedQualityGates.VULNERABILITIES)) {
      body.append(VulnerabilityCacheSteps.save(project));
    }
    return new OwnedSection(
        project.repositoryRoot().resolve(WORKFLOWS + fileName(project)),
        BEGIN,
        END,
        body.toString(),
        true);
  }

  /** A root project keeps {@code ward.yml}; a nested one gets a file named after its path. */
  static String fileName(final Project project) {
    if (!project.nested()) {
      return "ward.yml";
    }
    return "ward-" + project.pathSlug() + ".yml";
  }

  /** Code scanning keeps one result set per category, so each project needs its own. */
  private static String category(final Project project) {
    final String file = fileName(project);
    return file.substring(0, file.length() - ".yml".length());
  }

  private static String sarif(final Project project) {
    return project.nested() ? project.projectPath() + "/" + SARIF : SARIF;
  }

  private static String header(
      final Set<String> ids, final Project project, final String file, final ProjectFacts facts) {
    final boolean nested = project.nested();
    final String paths =
        nested
            ? "    paths: ["
                + quoted(project.projectPath() + "/**")
                + ", "
                + quoted(WORKFLOWS + file)
                + "]\n"
            : "";
    return "name: "
        + (nested ? quoted("Ward (" + project.projectPath() + ")") : "Ward")
        + "\n\n"
        + "on:\n"
        + "  pull_request:\n"
        + paths
        + "  push:\n"
        + "    branches: [main]\n"
        + paths
        + "\n"
        + "permissions:\n"
        + "  contents: read\n\n"
        + "jobs:\n"
        + "  quality:\n"
        + "    name: Ward ci profile\n"
        + "    runs-on: "
        + InitToolchains.support(project).runner()
        + "\n"
        // Shell steps run in the project; uses: steps ignore this, so SARIF paths stay rooted.
        + (nested
            ? "    defaults:\n      run:\n        working-directory: "
                + quoted(project.projectPath())
                + "\n"
            : "")
        + "    permissions:\n"
        + "      contents: read\n"
        + "      security-events: write\n"
        + "    steps:\n"
        + "      - uses: actions/checkout@v5\n"
        // openapi-compat needs the base branch and api-compat the release tags; init never turns on
        // the history scan of secrets.
        + (ids.contains("openapi-compat") || ids.contains("api-compat")
            ? "        with:\n          fetch-depth: 0\n"
            : "")
        + InitToolchains.support(project).ciSetup(project, facts);
  }

  private static String tools(final Set<String> ids) {
    final List<String> install = new ArrayList<>();
    if (ids.contains("semgrep")) {
      install.add("          pip install semgrep==" + SEMGREP_VERSION + "\n");
    }
    // The runner's system Python refuses global pip installs.
    final String python =
        ids.contains("semgrep")
            ? STEP
                + "Set up Python\n"
                + "        uses: actions/setup-python@v6\n"
                + "        with:\n"
                + "          python-version: '3.13'\n"
            : "";
    return install.isEmpty()
        ? ""
        : python
            + STEP
            + "Install gate tools\n        run: |\n          set -eu\n"
            + String.join("", install);
  }

  /** Quotes {@code value} as a single-quoted YAML scalar. */
  private static String quoted(final String value) {
    return "'" + value.replace("'", "''") + "'";
  }

  private static String ward(final String version, final String sarif, final String category) {
    return STEP
        + "Install Ward\n"
        + "        env:\n"
        + (DEVELOPMENT_VERSION.equals(version) ? "" : "          WARD_VERSION: v" + version + "\n")
        + "          WARD_INSTALL_DIR: ${{ runner.temp }}/ward/bin\n"
        + "        run: |\n"
        + "          curl -fsSL"
        + " https://raw.githubusercontent.com/RicardoRB/ward-ai/main/install.sh | sh\n"
        + "          echo \"$WARD_INSTALL_DIR\" >> \"$GITHUB_PATH\"\n"
        + STEP
        + "Run quality gates\n"
        + "        run: ward check --profile ci --format sarif --output ward.sarif\n"
        // Fork tokens cannot write security events; no file means Ward could not run at all.
        + STEP
        + "Upload SARIF to code scanning\n"
        + "        if: >-\n"
        + "          always()\n"
        + "          && github.event_name == 'pull_request'\n"
        + "          && github.event.pull_request.head.repo.full_name == github.repository\n"
        + "          && hashFiles('"
        + sarif.replace("'", "''")
        + "') != ''\n"
        + "        uses: github/codeql-action/upload-sarif@v4\n"
        + "        with:\n"
        + "          sarif_file: "
        + sarif
        + "\n"
        + "          category: "
        + category
        + "\n";
  }
}
