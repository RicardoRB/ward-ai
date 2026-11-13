package dev.ward.application.init;

import dev.ward.domain.init.InitGateOption;
import java.util.List;

/** The setup a toolchain gives the selected {@code crap} gate. */
@FunctionalInterface
public interface CrapSetup {
  /**
   * Returns {@code gates} with the setup the {@code crap} gate needs: it is placed after the gate
   * that produces the coverage data, and the toolchain adds the report to commands or checks.
   * Returns {@code gates} when {@code crap} is not among them.
   *
   * @throws dev.ward.domain.config.WardException if the gate that produces the data is missing
   */
  List<InitGateOption> apply(List<InitGateOption> gates);
}
