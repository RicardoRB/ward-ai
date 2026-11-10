class Point:
    __match_args__ = ("x", "y")


def where(value):
    match value:
        case Point(1, 2, 3):
            return 1
    return 0
