class Base:
    def run(self, value=1):
        return value


class Child(Base):
    def run(self, value):
        return value
