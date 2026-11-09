class C:
    pass


def f(x):
    match x:
        case C(a=1, a=2):
            return 1
