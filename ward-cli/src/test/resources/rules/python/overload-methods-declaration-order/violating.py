from typing import overload


@overload
def f(a: int) -> int: ...


def g() -> None:
    pass


@overload
def f(a: str) -> str: ...
