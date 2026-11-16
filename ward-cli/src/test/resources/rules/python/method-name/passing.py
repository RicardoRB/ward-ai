def parse_value(value):
    return value


class ParserTest(unittest.TestCase):
    def setUp(self):
        self.value = "1"

    def tearDown(self):
        self.value = None
