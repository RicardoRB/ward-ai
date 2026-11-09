class C:
    pass


def f(x):
    match x:
        case C(a=1, b=2):
            return 1
