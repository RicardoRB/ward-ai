class C:
    __match_args__ = ("a",)


def f(x):
    match x:
        case C(1):
            return 1
