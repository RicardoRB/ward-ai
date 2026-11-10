import org.apache.commons.logging.Log;

class Sample {
  Log log = LogFactory.getLog(Sample.class);
  private static final Log LOG = LogFactory.getLog(Other.class);
  private static final Log logger = LogFactory.getLog(Sample.class);
}
