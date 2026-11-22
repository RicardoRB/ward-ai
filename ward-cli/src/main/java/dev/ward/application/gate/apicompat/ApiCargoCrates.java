package dev.ward.application.gate.apicompat;

import dev.ward.domain.gate.cargo.CargoMembers;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/** Finds the Cargo crates of a file tree: a directory with {@code Cargo.toml} and the members. */
final class ApiCargoCrates {
  private static final String POM = "pom.xml";
  private static final List<String> GRADLE = List.of("build.gradle.kts", "build.gradle");

  private ApiCargoCrates() {}

  /** Returns the workspace members {@code dir}'s manifest lists, with globs expanded. */
  static List<String> members(
      final Function<String,
      Optional<String>> read,
      final Function<String,
      List<String>> files,
      final String dir
  ) {
    final List<String> found = new ArrayList<>();
    read.apply(join(dir, CargoMembers.MANIFEST)).ifPresent(
        manifest -> CargoMembers.listed(manifest).forEach(
            member -> found.addAll(expand(files, dir, member))
        )
    );
    return found;
  }

  /** Returns whether {@code dir} has a {@code Cargo.toml}. */
  static boolean has(final Function<String, Optional<String>> read, final String dir) {
    return read.apply(join(dir, CargoMembers.MANIFEST)).isPresent();
  }

  /** Returns whether {@code dir} is a crate that has no Maven or Gradle build. */
  static boolean only(final Function<String, Optional<String>> read, final String dir) {
    return has(read, dir)
        && read.apply(join(dir, POM)).isEmpty()
        && GRADLE.stream().allMatch(script -> read.apply(join(dir, script)).isEmpty());
  }

  /** A member of a Cargo workspace; {@code crates/*} stands for each crate below {@code crates}. */
  private static List<String> expand(
      final Function<String,
      List<String>> files,
      final String dir,
      final String member
  ) {
    if (!member.endsWith(CargoMembers.CHILDREN)) {
      return List.of(member);
    }
    final String parent = member.substring(0, member.length() - CargoMembers.CHILDREN.length());
    final String suffix = "/" + CargoMembers.MANIFEST;
    return files.apply(join(dir, parent)).stream()
        .filter(
            file -> file.endsWith(suffix) && file.indexOf('/') == file.length() - suffix.length()
        )
        .map(file -> parent + "/" + file.substring(0, file.length() - suffix.length()))
        .toList();
  }

  private static String join(final String dir, final String name) {
    return dir.isEmpty() ? name : dir + "/" + name;
  }
}
