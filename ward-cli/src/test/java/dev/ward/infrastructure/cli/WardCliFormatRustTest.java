package dev.ward.infrastructure.cli;

import static dev.ward.infrastructure.cli.FormatTestFiles.read;
import static dev.ward.infrastructure.cli.FormatTestFiles.write;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** {@code ward format} over Rust files, story R3 of epic #642. */
@DisabledOnOs(OS.WINDOWS)
class WardCliFormatRustTest extends WardCliIntegrationSupport {
  private static final String FORMAT = "format";
  private static final String CHECK_FLAG = "--check";
  private static final String FILE = "src/main.rs";
  private static final String RAW =
      "fn main(){\n      let a=1+2;\n   println!(\"{}\",a);\n}\n";
  private static final String FORMATTED =
      "fn main() {\n    let a = 1 + 2;\n    println!(\"{}\", a);\n}\n";

  @Test
  @DisplayName("an unformatted Rust file")
  void anUnformattedRustFile() throws IOException {
    write(project, FILE, RAW);

    final int check = run(FORMAT, CHECK_FLAG, "-C", project.toString());
    assertThat(check).isEqualTo(ExitCodes.FAILED);
    assertThat(out.toString()).contains("needs formatting " + FILE);

    assertThat(run(FORMAT, "-C", project.toString())).isEqualTo(ExitCodes.OK);
    assertThat(read(project, FILE)).isEqualTo(FORMATTED);
    assertThat(run(FORMAT, CHECK_FLAG, "-C", project.toString())).isEqualTo(ExitCodes.OK);
  }
}
