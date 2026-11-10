







import org.apache.commons.logging.Log;

class Sample {
  private static final Log LOG = LogFactory.getLog(Sample.class);
  private final Log log;

  Sample(Log log) {
    this.log = log;
  }

  private int other;
}
