def load_config():
    return 1


class UserStore:
    def __init__(self):
        self._cache = 1


import unittest


class CartTest(unittest.TestCase):
    def setUp(self):
        self.items = []


Lookups = Iterable[tuple[str, str]]
Number = int | float
Item = TypeVar("Item")
