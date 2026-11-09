from contextlib import contextmanager


@contextmanager
def cm():
    conn = object()
    try:
        yield conn
    except GeneratorExit:
        pass
    print('done')


def gen():
    with cm():
        yield 2
