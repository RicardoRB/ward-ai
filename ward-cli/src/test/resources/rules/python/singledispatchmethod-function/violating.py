from functools import singledispatchmethod


@singledispatchmethod
def show(value):
    return value
