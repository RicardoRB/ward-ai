from contextlib import contextmanager


@contextmanager
def cm():
    conn = object()
    yield conn
    print('done')


def gen():
    with cm():
        yield 2
