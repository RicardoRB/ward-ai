from abc import ABC, abstractmethod


class Base(ABC):
    @abstractmethod
    def run(self):
        ...


class Child(Base):
    def run(self):
        return 1
