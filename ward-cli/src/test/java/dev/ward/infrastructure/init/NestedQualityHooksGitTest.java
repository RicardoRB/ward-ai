package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.project.FileSystemProjectInspection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** In a real Git repository, nested projects install into the hooks Git runs and run scoped. */
@DisabledOnOs(OS.WINDOWS)
class NestedQualityHooksGitTest {
  private static final String POM = "<project><artifactId>api</artifactId></project>";
  private static final String USER_HOOK = "#!/bin/sh\necho user formatter\n";

  @TempDir Path repository;
  @TempDir Path bin;
  private GitHookRepository hooks;

  private final QualityHookInstaller installer = new QualityHookInstaller();
  private Project api;

  @BeforeEach
  void repositoryWithNestedProject() throws IOException, InterruptedException {
    hooks = new GitHookRepository(repository, bin);
    hooks.git("init", "-q");
    final Path root = Files.createDirectories(repository.resolve("apps/api"));
    Files.writeString(root.resolve("pom.xml"), POM);
    api = new FileSystemProjectInspection().detect(root).orElseThrow();
  }

  @Test
  void nestedProjectExtendsTheRepositoryHooksAndCreatesNothingInsideIt() throws IOException {
    final Path preCommit = repository.resolve(".git/hooks/pre-commit");
    Files.writeString(preCommit, USER_HOOK);

    assertThat(installer.plan(api))
        .extracting(PlannedFileChange::file, PlannedFileChange::action)
        .containsExactly(
            tuple(preCommit, PlannedFileChange.Action.MODIFY),
            tuple(repository.resolve(".git/hooks/pre-push"), PlannedFileChange.Action.CREATE));
    installer.apply(api);

    assertThat(Files.readString(preCommit))
        .contains("echo user formatter", "# ward-owned: quality-hooks v1 begin apps/api");
    assertThat(repository.resolve("apps/api/.git")).doesNotExist();
  }

  @Test
  void configuredHooksPathReceivesTheSections() throws IOException, InterruptedException {
    hooks.git("config", "core.hooksPath", ".githooks");

    installer.apply(api);

    assertThat(repository.resolve(".githooks/pre-commit")).exists();
    assertThat(repository.resolve(".githooks/pre-push")).exists();
    assertThat(repository.resolve(".git/hooks/pre-commit")).doesNotExist();
  }

  @Test
  void preCommitSkipsCommitsThatDoNotTouchTheProject() throws IOException, InterruptedException {
    final Path calls = hooks.stubWard();
    installer.apply(api);
    Files.createDirectories(repository.resolve("apps/web"));
    Files.writeString(repository.resolve("apps/web/page.tsx"), "export {}\n");
    hooks.git("add", "apps/web/page.tsx");

    assertThat(hooks.runHook("pre-commit")).isZero();
    assertThat(calls).doesNotExist();
  }

  @Test
  void preCommitRunsFromTheProjectWhenItIsStaged() throws IOException, InterruptedException {
    final Path calls = hooks.stubWard();
    installer.apply(api);
    hooks.git("add", "apps/api/pom.xml");

    assertThat(hooks.runHook("pre-commit")).isZero();
    assertThat(Files.readString(calls).strip())
        .isEqualTo(
            GitHookRepository.real(repository.resolve("apps/api"))
                + " check --profile pre-commit --format json --path=pom.xml");
  }

  @Test
  void preCommitPassesStagedFilesWithSpacesAndAccentsAsOneArgumentEach()
      throws IOException, InterruptedException {
    final Path arguments = bin.resolve("arguments");
    final Path ward = bin.resolve("ward");
    Files.writeString(ward, "#!/bin/sh\nfor a; do echo \"$a\"; done >> '" + arguments + "'\n");
    Files.setPosixFilePermissions(ward, PosixFilePermissions.fromString("rwxr-xr-x"));
    installer.apply(api);
    Files.createDirectories(repository.resolve("apps/api/src"));
    Files.writeString(repository.resolve("apps/api/src/Más datos.java"), "class A {}\n");
    hooks.git("add", "apps/api/pom.xml", "apps/api/src/Más datos.java");

    assertThat(hooks.runHook("pre-commit")).isZero();
    assertThat(Files.readAllLines(arguments))
        .containsExactly(
            "check",
            "--profile",
            "pre-commit",
            "--format",
            "json",
            "--path=pom.xml",
            "--path=src/Más datos.java");
  }

  @Test
  void prePushPassesTheFilesChangedSinceTheUpstream() throws IOException, InterruptedException {
    commitBaseWithUpstream();
    Files.createDirectories(repository.resolve("apps/api/src"));
    Files.writeString(repository.resolve("apps/api/src/Más datos.java"), "class A {}\n");
    hooks.git("add", "-A");
    hooks.git("commit", "-q", "-m", "change");
    final Path calls = hooks.stubWard();
    installer.apply(api);

    assertThat(hooks.runHook("pre-push")).isZero();
    assertThat(Files.readString(calls).strip())
        .isEqualTo(
            GitHookRepository.real(repository.resolve("apps/api"))
                + " check --profile pre-push --format json --path=src/Más datos.java");
  }

  @Test
  void prePushRunsInFullWithoutAnUpstream() throws IOException, InterruptedException {
    hooks.git("config", "user.email", "ward@example.com");
    hooks.git("config", "user.name", "ward");
    hooks.git("add", "-A");
    hooks.git("commit", "-q", "-m", "base");
    final Path calls = hooks.stubWard();
    installer.apply(api);

    assertThat(hooks.runHook("pre-push")).isZero();
    assertThat(Files.readString(calls).strip())
        .isEqualTo(
            GitHookRepository.real(repository.resolve("apps/api"))
                + " check --profile pre-push --format json");
  }

  /** Commits the project, and makes that commit the upstream of the current {@code main} branch. */
  private void commitBaseWithUpstream() throws IOException, InterruptedException {
    hooks.git("symbolic-ref", "HEAD", "refs/heads/main");
    hooks.git("config", "user.email", "ward@example.com");
    hooks.git("config", "user.name", "ward");
    hooks.git("add", "-A");
    hooks.git("commit", "-q", "-m", "base");
    hooks.git("remote", "add", "origin", "https://example.invalid/ward.git");
    hooks.git("update-ref", "refs/remotes/origin/main", "HEAD");
    hooks.git("config", "branch.main.remote", "origin");
    hooks.git("config", "branch.main.merge", "refs/heads/main");
  }
}
