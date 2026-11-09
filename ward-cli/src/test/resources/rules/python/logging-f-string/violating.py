import logging
x = 1
logging.info(f"a {x}")
logging.getLogger(__name__).info(f"a {x}")
