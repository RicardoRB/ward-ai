class A:
    def __init__(self):
        def inner():
            yield 1
        self.x = inner


def f():
    yield 1
