class C:
    __match_args__ = ("a",)


def f(x):
    match x:
        case C(a=1):
            return 1
