class Base:
    def __init__(self, value):
        self.value = value


class Child(Base):
    def __init__(self, value):
        super().__init__(value)
