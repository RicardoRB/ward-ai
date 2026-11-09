from functools import singledispatch


@singledispatch
def show(value):
    return value
