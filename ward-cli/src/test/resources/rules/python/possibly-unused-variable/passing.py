def f():
    x = 1
    print(x)
    return locals()


def h():
    z = 1
    def inner():
        return locals()
    return inner
