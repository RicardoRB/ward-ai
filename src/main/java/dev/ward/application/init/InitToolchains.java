package dev.ward.application.init;

import dev.ward.application.init.jvm.JvmInitSupport;
import dev.ward.application.init.node.NodeInitSupport;
import dev.ward.application.init.python.PythonInitSupport;
import dev.ward.application.init.swift.SwiftInitSupport;
import dev.ward.domain.config.WardException;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.Toolchain;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The toolchains init can configure, each with its init support. */
public final class InitToolchains {
  // An EnumMap iterates in toolchain order, so shared output lists toolchains the same way each
  // run.
  private static final Map<Toolchain, ToolchainInitSupport> SUPPORTS =
      new EnumMap<>(
          Map.of(
              Toolchain.JVM,
              new JvmInitSupport(),
              Toolchain.NODE,
              new NodeInitSupport(),
              Toolchain.PYTHON,
              new PythonInitSupport(),
              Toolchain.SWIFT,
              new SwiftInitSupport()));

  private InitToolchains() {}

  /** Returns every toolchain's init support, in toolchain order. */
  static List<ToolchainInitSupport> all() {
    return List.copyOf(SUPPORTS.values());
  }

  /** Returns {@code gates} with the user's existing {@code tests} reused by the toolchain. */
  static List<InitGateOption> reuseTests(
      final Project project, final List<InitGateOption> gates, final List<String> tests) {
    return find(project).map(support -> support.reuseTests(project, gates, tests)).orElse(gates);
  }

  /** Returns the init support for {@code project}'s toolchain, or empty when there is none. */
  static Optional<ToolchainInitSupport> find(final Project project) {
    return find(project.toolchain());
  }

  /** Returns the init support for {@code toolchain}, or empty when init has none. */
  static Optional<ToolchainInitSupport> find(final Toolchain toolchain) {
    return Optional.ofNullable(SUPPORTS.get(toolchain));
  }

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
  public static ToolchainInitSupport support(final Project project) {
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
    return "Ward init supports Maven, Gradle, TypeScript, JavaScript, Python (uv, Poetry, pip) and"
        + " Swift (SwiftPM, Xcode) projects; "
        + project.buildTool().displayName()
        + " projects are not supported yet.";
  }
}
