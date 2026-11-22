package dev.ward.infrastructure.cli;

import static dev.ward.infrastructure.cli.FormatTestFiles.read;
import static dev.ward.infrastructure.cli.FormatTestFiles.write;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** {@code ward format} on C# files. */
@DisabledOnOs(OS.WINDOWS)
class WardCliFormatCSharpTest extends WardCliIntegrationSupport {
  private static final String FILE = "src/Cart.cs";
  private static final String RAW = "namespace Shop{\nclass Cart{\n  int  total=1+2;\n}\n}\n";

  @DisplayName("an unformatted C# file")
  @Test
  void anUnformattedCSharpFileIsNamedThenRewrittenAndPassesCheck() throws IOException {
    write(project, FILE, RAW);

    assertThat(run("format", "--check", "-C", project.toString())).isEqualTo(ExitCodes.FAILED);
    assertThat(out.toString()).contains("needs formatting " + FILE);

    assertThat(run("format", "-C", project.toString())).isEqualTo(ExitCodes.OK);
    assertThat(read(project, FILE))
        .isEqualTo(
            "namespace Shop\n{\n    class Cart\n    {\n        int total = 1 + 2;\n    }\n}\n"
        );
    out.getBuffer().setLength(0);
    assertThat(run("format", "--check", "-C", project.toString())).isEqualTo(ExitCodes.OK);
  }
}
