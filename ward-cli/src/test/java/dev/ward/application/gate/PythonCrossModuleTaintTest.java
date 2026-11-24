package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.application.rule.RuleEngine;
import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.frontend.antlr.AntlrLanguageFrontend;
import dev.ward.infrastructure.analysis.GateFrontends;
import dev.ward.infrastructure.analysis.python.PythonDependencyAdapters;
import dev.ward.infrastructure.rule.ClasspathRuleSource;
import dev.ward.infrastructure.rule.DeclarativeRuleMatcher;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A call of a function of another Python module is followed through its module-qualified name. */
class PythonCrossModuleTaintTest {
  private static final String PATH_RULE = "ward:python/path-injection";
  private static final String REDIRECT_RULE = "ward:python/untrusted-redirect";
  private static final String EXPORTS =
      "from io import StringIO\n"
          + "from flask import request\n"
          + "\n"
          + "\n"
          + "def dump_csv(name):\n"
          + "    return StringIO()\n"
          + "\n"
          + "\n"
          + "def wanted():\n"
          + "    return request.args.get(\"f\")\n";
  private static final String VIEWS =
      "import shop.exports as ex\n"
          + "from flask import request, send_file\n"
          + "from shop.exports import dump_csv, wanted\n"
          + "\n"
          + "\n"
          + "def named():\n"
          + "    return send_file(dump_csv(request.args.get(\"n\")))\n"
          + "\n"
          + "\n"
          + "def aliased():\n"
          + "    return send_file(ex.dump_csv(request.args.get(\"n\")))\n"
          + "\n"
          + "\n"
          + "def tainted():\n"
          + "    return send_file(wanted())\n"
          + "\n"
          + "\n"
          + "def tainted_through_alias():\n"
          + "    return send_file(ex.wanted())\n";
  private static final int NOT_EXITING_LINE = 23;
  private static final int UNRELATED_CHECK_LINE = 30;
  private static final int ALWAYS_TRUE_LINE = 28;
  private static final int OTHER_VALUE_LINE = 36;
  private static final int TAINTED_LINE = 15;
  private static final int ALIASED_LINE = 19;

  @TempDir Path root;

  private final List<FrontendSupport> frontends = GateFrontends.create(new AntlrLanguageFrontend());

  private List<AnalysisFinding> run(final String rule, final List<SourceUnit> units) {
    final List<RuleDefinition> rules =
        new ClasspathRuleSource()
            .load().rules().stream().filter(candidate -> rule.equals(candidate.id())).toList();
    return new UnitResultCache(
        new RuleEngine(new DeclarativeRuleMatcher()),
        AnalysisCacheFixtures.codec(),
        "test",
        new PythonDependencyAdapters().forProject(root, units)
    )
        .run(AnalysisCacheFixtures.NO_CACHE, units, frontends, rules)
        .findings();
  }

  private static SourceUnit python(final String path, final String text) {
    return new SourceUnit(path, AnalysisLanguage.PYTHON, text);
  }

  @Test
  void aBufferBuiltInAnotherModuleIsNotAPathButASourceThereStillIs() {
    final List<AnalysisFinding> found =
        run(
            PATH_RULE,
            List.of(
                python("shop/__init__.py", ""),
                python("shop/exports.py", EXPORTS),
                python("shop/views.py", VIEWS)
            )
        );

    assertThat(found).extracting(AnalysisFinding::file).containsOnly("shop/views.py");
    assertThat(found)
        .extracting(AnalysisFinding::line)
        .containsExactlyInAnyOrder(TAINTED_LINE, ALIASED_LINE);
  }

  @Test
  void aValidatorOfAnotherModuleGuardsTheRedirect() {
    final String checks =
        "from urllib.parse import urlparse\n"
            + "from flask import request\n"
            + "\n"
            + "\n"
            + "def is_safe(target):\n"
            + "    return urlparse(target).netloc == request.host\n"
            + "\n"
            + "\n"
            + "def is_even(target):\n"
            + "    return len(target) % 2 == 0\n";
    final String views =
        "from flask import abort, redirect, request\n"
            + "from shop.checks import is_even, is_safe\n"
            + "\n"
            + "\n"
            + "def guarded():\n"
            + "    target = request.args.get(\"next\")\n"
            + "    if not is_safe(target):\n"
            + "        abort(400)\n"
            + "    return redirect(target)\n"
            + "\n"
            + "\n"
            + "def positive():\n"
            + "    target = request.args.get(\"next\")\n"
            + "    if is_safe(target):\n"
            + "        return redirect(target)\n"
            + "    return abort(400)\n"
            + "\n"
            + "\n"
            + "def does_not_exit():\n"
            + "    target = request.args.get(\"next\")\n"
            + "    if not is_safe(target):\n"
            + "        print(\"unsafe\")\n"
            + "    return redirect(target)\n"
            + "\n"
            + "\n"
            + "def checks_nothing_about_the_host():\n"
            + "    target = request.args.get(\"next\")\n"
            + "    if not is_even(target):\n"
            + "        abort(400)\n"
            + "    return redirect(target)\n";
    final List<AnalysisFinding> found =
        run(
            REDIRECT_RULE,
            List.of(
                python("shop/__init__.py", ""),
                python("shop/checks.py", checks),
                python("shop/views.py", views)
            )
        );

    assertThat(found).extracting(AnalysisFinding::line).containsExactlyInAnyOrder(
        NOT_EXITING_LINE,
        UNRELATED_CHECK_LINE
    );
  }

  @Test
  void aValidatorOfTheSameFileThatRejectsAbsoluteUrlsGuardsTheRedirect() {
    final String views =
        "from urllib.parse import urlparse\n"
            + "from flask import redirect, request\n"
            + "\n"
            + "\n"
            + "def relative_only(url):\n"
            + "    if not url:\n"
            + "        return False\n"
            + "    parsed = urlparse(url)\n"
            + "    if parsed.netloc:\n"
            + "        return False\n"
            + "    return parsed.path.startswith(\"/\")\n"
            + "\n"
            + "\n"
            + "def always(url):\n"
            + "    return url.startswith(\"/\")\n"
            + "\n"
            + "\n"
            + "def after_login():\n"
            + "    target = request.args.get(\"next\")\n"
            + "    if target and relative_only(target):\n"
            + "        return redirect(target)\n"
            + "    return redirect(\"/\")\n"
            + "\n"
            + "\n"
            + "def unchecked():\n"
            + "    target = request.args.get(\"next\")\n"
            + "    if target and always(target):\n"
            + "        return redirect(target)\n"
            + "    return redirect(\"/\")\n"
            + "\n"
            + "\n"
            + "def other_value():\n"
            + "    target = request.args.get(\"next\")\n"
            + "    other = request.args.get(\"other\")\n"
            + "    if target and relative_only(target):\n"
            + "        return redirect(other)\n"
            + "    return redirect(\"/\")\n";
    final List<AnalysisFinding> found =
        run(REDIRECT_RULE, List.of(python("shop/__init__.py", ""), python("shop/views.py", views)));

    assertThat(found).extracting(AnalysisFinding::line).containsExactlyInAnyOrder(
        ALWAYS_TRUE_LINE,
        OTHER_VALUE_LINE
    );
  }
}
