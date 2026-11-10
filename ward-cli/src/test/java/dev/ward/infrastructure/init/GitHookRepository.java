package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A Git repository whose installed hooks run here, with a stub {@code ward} first on the PATH. The
 * stub records its working directory and arguments in {@code calls}.
 */
final class GitHookRepository {
  private final Path repository;
  private final Path bin;

  /** Wraps {@code repository}; the stub {@code ward} is written into {@code bin}. */
  GitHookRepository(final Path repository, final Path bin) {
    this.repository = repository;
    this.bin = bin;
  }

  /** A stub {@code ward} on the PATH that records its working directory and arguments. */
  Path stubWard() throws IOException {
    final Path calls = bin.resolve("calls");
    final Path ward = bin.resolve("ward");
    Files.writeString(ward, "#!/bin/sh\necho \"$(pwd -P) $*\" >> '" + calls + "'\n");
    Files.setPosixFilePermissions(ward, PosixFilePermissions.fromString("rwxr-xr-x"));
    return calls;
  }

  int runHook(final String name) throws IOException, InterruptedException {
    final ProcessBuilder builder =
        new ProcessBuilder(real(repository.resolve(".git/hooks").resolve(name)).toString())
            .directory(repository.toFile())
            .redirectErrorStream(true);
    final Map<String, String> environment = builder.environment();
    environment.put("PATH", bin + ":" + environment.getOrDefault("PATH", "/usr/bin:/bin"));
    final Process process = builder.start();
    process.getInputStream().readAllBytes();
    return process.waitFor();
  }

  void git(final String... args) throws IOException, InterruptedException {
    final List<String> command = new ArrayList<>(List.of("git"));
    command.addAll(List.of(args));
    final Process process =
        new ProcessBuilder(command)
            .directory(repository.toFile())
            .redirectErrorStream(true)
            .start();
    final String output =
        new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    assertThat(process.waitFor()).as(output).isZero();
  }

  static Path real(final Path path) throws IOException {
    final Path parent = path.getParent().toRealPath();
    return parent.resolve(path.getFileName());
  }
}
