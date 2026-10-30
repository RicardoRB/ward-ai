package dev.ward.application.init;

import dev.ward.domain.config.WardException;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.Toolchain;
import java.util.Map;

/** The toolchains init can configure, each with its init support. */
final class InitToolchains {
  private static final Map<Toolchain, ToolchainInitSupport> SUPPORTS =
      Map.of(
          Toolchain.JVM,
          new JvmInitSupport(),
          Toolchain.NODE,
          new NodeInitSupport(),
          Toolchain.PYTHON,
          new PythonInitSupport());

  private InitToolchains() {}

  /** Returns whether init can configure {@code project}, reading it through {@code inputs}. */
  static boolean supported(final Project project, final InitInputs inputs) {
    final ToolchainInitSupport support = SUPPORTS.get(project.toolchain());
    return support != null && support.supports(project, inputs);
  }

  /**
   * Returns the init support for {@code project}'s toolchain.
   *
   * @throws WardException if init does not support that toolchain
   */
  static ToolchainInitSupport support(final Project project) {
    final ToolchainInitSupport support = SUPPORTS.get(project.toolchain());
    if (support == null) {
      throw new WardException(unsupported(project));
    }
    return support;
  }

  /** Returns whether init has no support at all for {@code project}'s toolchain. */
  static boolean unknown(final Project project) {
    final ToolchainInitSupport support = SUPPORTS.get(project.toolchain());
    return support == null || !support.handles(project);
  }

  /** Returns the message explaining that init cannot configure {@code project}'s toolchain. */
  static String unsupported(final Project project) {
    return "Ward init supports Maven, Gradle, TypeScript, JavaScript and Python (uv, Poetry, pip)"
        + " projects; "
        + project.buildTool().displayName()
        + " projects are not supported yet.";
  }
}
