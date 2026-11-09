import logging
logging.info("a {}".format(1))
logging.getLogger(__name__).info("a {}".format(1))
