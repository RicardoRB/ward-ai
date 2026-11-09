def f():
    y = 1

    def g():
        nonlocal y
        return y
