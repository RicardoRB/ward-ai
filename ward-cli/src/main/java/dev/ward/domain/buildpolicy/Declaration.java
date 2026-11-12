package dev.ward.domain.buildpolicy;

import java.util.Objects;

/**
 * One plugin, dependency or build requirement that a build file declares.
 *
 * @param ecosystem the build files it was read from
 * @param kind what it is
 * @param name its name, such as {@code group:artifact}, a plugin id or a package name
 * @param version the version as the project's own files give it, with properties resolved when they
 *     are defined there; {@code null} when the declaration has none
 * @param section where it was declared, so that equal declarations in different sections differ
 * @param identity what makes two declarations the same dependency; empty when it does not matter
 * @param file the project-relative path of the build file, with {@code /}
 * @param line the 1-based line, or {@code 0} when the format gives none
 * @param flag a fact about the declaration that a rule needs
 */
public record Declaration(
    BuildEcosystem ecosystem,
    DeclarationKind kind,
    String name,
    String version,
    String section,
    String identity,
    String file,
    int line,
    Flag flag) {

  /** A fact about a declaration that is not its text. */
  public enum Flag {
    /** Nothing special. */
    NONE,
    /** The version is not a literal, so it cannot be read from the file. */
    UNDETERMINABLE,
    /** A lockfile of the ecosystem sits next to the build file. */
    LOCKED,
    /** A Maven plugin has no version and the POM has a parent outside the project. */
    EXTERNAL_PARENT
  }

  /** Checks the required parts. */
  public Declaration {
    Objects.requireNonNull(ecosystem, "ecosystem");
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(section, "section");
    Objects.requireNonNull(identity, "identity");
    Objects.requireNonNull(file, "file");
    Objects.requireNonNull(flag, "flag");
  }

  /** Returns whether the declaration has a version text. */
  public boolean versioned() {
    return version != null && !version.isBlank();
  }
}
