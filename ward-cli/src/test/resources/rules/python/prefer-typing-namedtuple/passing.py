from typing import NamedTuple


class Point(NamedTuple):
    x: int
    y: int

Dynamic = collections.namedtuple("Dynamic", fields)
