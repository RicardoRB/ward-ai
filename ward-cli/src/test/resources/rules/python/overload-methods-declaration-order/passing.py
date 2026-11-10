from typing import overload


@overload
def f(a: int) -> int: ...


@overload
def f(a: str) -> str: ...
