package fixture;

import java.sql.Statement;

class Flow {
  void run(Statement st, String text) {
    String raw = source();
    String query = "select " + raw;
    sink(query);
  }
}
