from abc import ABC, abstractmethod


class Base(ABC):
    @abstractmethod
    def run(self):
        pass


class Concrete(Base):
    def run(self):
        return 1


c = Concrete()
