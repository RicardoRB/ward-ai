


final class T {
  static final String Q = "q";
  void run() {
    Sql.query("select 1");
    Sql.query(Q);
    Other.query(name());
  }
}
