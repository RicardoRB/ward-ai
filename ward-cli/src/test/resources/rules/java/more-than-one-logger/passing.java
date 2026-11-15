import java.util.logging.Logger;

class Service {
  private static final Logger LOG = Logger.getLogger("service");
}

class Gate {
  private static final Logger LOG = Logger.getLogger("gate");

  void run() {
    final Logger reports = Logger.getLogger("reports");
    final Logger audit = Logger.getLogger("audit");
  }
}
