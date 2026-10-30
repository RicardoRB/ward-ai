package dev.ward.infrastructure.doctor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.FakeCommandExecutor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Finds the FastAPI application so the OpenAPI export hint can name it. */
class FastApiAppInspectionTest {
  @TempDir Path root;

  @Test
  void findsTheAppInMainPyAtTheRoot() throws IOException {
    Files.writeString(root.resolve("main.py"), "from fastapi import FastAPI\n\napi = FastAPI()\n");

    assertThat(new SystemEnvironmentInspection(new FakeCommandExecutor(1, "", "")).fastApiApp(root))
        .contains("main:api");
  }

  @Test
  void findsTheAppUnderTheAppPackage() throws IOException {
    Files.createDirectories(root.resolve("app"));
    Files.writeString(root.resolve("app/main.py"), "app = FastAPI()\n");

    assertThat(new SystemEnvironmentInspection(new FakeCommandExecutor(1, "", "")).fastApiApp(root))
        .contains("app.main:app");
  }

  @Test
  void noFastApiAppMeansNoModule() {
    assertThat(new SystemEnvironmentInspection(new FakeCommandExecutor(1, "", "")).fastApiApp(root))
        .isEmpty();
  }
}
