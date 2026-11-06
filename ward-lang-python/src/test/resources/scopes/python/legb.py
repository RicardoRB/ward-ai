import os, sys as system
from a.b import c as d, e

counter = 0


def outer(a, b=counter, *args, **kwargs):
    total = a + b
    helper = 1

    def inner():
        nonlocal total
        total = total + 1
        return helper

    return inner, d, os, undefined_name


def setter():
    global counter, created
    counter = 2
    created = 3
    unused = 4


class Box:
    size = 10
    other = size * 2

    def method(self):
        return size

    def read(self):
        return self.size + counter
