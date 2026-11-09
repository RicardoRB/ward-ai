import logging
logging.info("a %s" % 1)
logging.getLogger(__name__).info("a %s" % 1)
