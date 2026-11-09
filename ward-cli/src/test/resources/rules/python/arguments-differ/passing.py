class Base:
    def run(self, a, b):
        return a


class Child(Base):
    def run(self, a, b, extra=None):
        return a
