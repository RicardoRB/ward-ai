package dev.ward.infrastructure.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.config.WardException;
import dev.ward.domain.process.CommandResult;
import dev.ward.domain.project.XcodeSchemes;
import dev.ward.infrastructure.json.ForyJsonCodec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Lists Xcode schemes from {@code xcodebuild -list -json} and reads the platform of a project. */
class XcodebuildSchemesTest {
  private static final String SHOP = "Shop";
  private static final String SHOP_PROJECT = "Shop.xcodeproj";

  private static final String PROJECT_JSON =
      "{\"project\": {\"name\": \"Shop\", \"schemes\": [\"Shop\"]}}";
  private static final String TWO_SCHEMES_JSON =
      "{\"project\": {\"schemes\": [\"Shop\", \"ShopWidgets\"]}}";
  private static final String WORKSPACE_JSON =
      "{\"workspace\": {\"name\": \"Shop\", \"schemes\": [\"Shop\", \"ShopTests\"]}}";

  @TempDir Path root;

  @Test
  void readsOneSchemeFromAProjectListing() throws IOException {
    makeProject(root, SHOP_PROJECT);
    shareScheme(root, SHOP_PROJECT, SHOP);
    final XcodeSchemes schemes = schemesRunning(PROJECT_JSON);

    assertThat(schemes.schemes(root)).containsExactly(SHOP);
  }

  @Test
  void autoCreatedSchemesAreNotListed() throws IOException {
    makeProject(root, SHOP_PROJECT);
    shareScheme(root, SHOP_PROJECT, SHOP);

    assertThat(
            schemesRunning("{\"project\": {\"schemes\": [\"Shop\", \"ShopTests\"]}}").schemes(root))
        .containsExactly(SHOP);
  }

  @Test
  void readsSeveralSchemesInListingOrder() throws IOException {
    makeProject(root, SHOP_PROJECT);
    shareScheme(root, SHOP_PROJECT, SHOP);
    shareScheme(root, SHOP_PROJECT, "ShopWidgets");

    assertThat(schemesRunning(TWO_SCHEMES_JSON).schemes(root)).containsExactly(SHOP, "ShopWidgets");
  }

  @Test
  void usesTheWorkspaceListingWhenAWorkspaceExists() throws IOException {
    makeProject(root, SHOP_PROJECT);
    shareScheme(root, SHOP_PROJECT, SHOP);
    Files.createDirectory(root.resolve("Shop.xcworkspace"));
    final FakeCommandExecutor executor = new FakeCommandExecutor(result(WORKSPACE_JSON));

    final List<String> schemes = new XcodebuildSchemes(executor, new ForyJsonCodec()).schemes(root);

    assertThat(schemes).containsExactly(SHOP);
    assertThat(executor.commands.getFirst())
        .containsExactly("xcodebuild", "-list", "-json", "-workspace", "Shop.xcworkspace");
  }

  @Test
  void usesTheProjectFlagWhenThereIsNoWorkspace() throws IOException {
    makeProject(root, SHOP_PROJECT);
    final FakeCommandExecutor executor = new FakeCommandExecutor(result(PROJECT_JSON));

    new XcodebuildSchemes(executor, new ForyJsonCodec()).schemes(root);

    assertThat(executor.commands.getFirst())
        .containsExactly("xcodebuild", "-list", "-json", "-project", SHOP_PROJECT);
  }

  @Test
  void failsWhenXcodebuildCannotList() throws IOException {
    makeProject(root, SHOP_PROJECT);
    final XcodeSchemes schemes =
        new XcodebuildSchemes(
            new FakeCommandExecutor(new CommandResult(70, "", "boom", Duration.ZERO)),
            new ForyJsonCodec());

    assertThatThrownBy(() -> schemes.schemes(root)).isInstanceOf(WardException.class);
  }

  private static XcodeSchemes schemesRunning(final String json) {
    return new XcodebuildSchemes(new FakeCommandExecutor(result(json)), new ForyJsonCodec());
  }

  private static CommandResult result(final String json) {
    return new CommandResult(0, json, "", Duration.ZERO);
  }

  private static void makeProject(final Path root, final String name) throws IOException {
    Files.createDirectories(root.resolve(name));
  }

  private static void shareScheme(final Path root, final String container, final String name)
      throws IOException {
    final Path schemes = root.resolve(container).resolve("xcshareddata/xcschemes");
    Files.createDirectories(schemes);
    Files.writeString(schemes.resolve(name + ".xcscheme"), "<Scheme/>");
  }
}
