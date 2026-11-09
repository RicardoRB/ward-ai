class A:
    x = 0

    def __init__(self):
        self.a = 1
        self.setup()

    def setup(self):
        self.c = 3

    def g(self):
        self.x = 5

    @property
    def p(self):
        return self._p

    @p.setter
    def p(self, v):
        self._p = v
