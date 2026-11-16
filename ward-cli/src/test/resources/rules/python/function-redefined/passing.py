from typing import overload


class A:
    @property
    def f(self):
        return 1

    @f.setter
    def f(self, v):
        pass


if True:
    def g():
        pass
else:
    def g():
        pass


@overload
def read(name: str) -> str: ...


@overload
def read(name: str, default: int) -> int: ...


def read(name, default=None):
    return default if name is None else name
