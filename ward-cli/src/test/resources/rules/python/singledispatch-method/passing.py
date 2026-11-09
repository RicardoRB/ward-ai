from functools import singledispatchmethod


class Printer:
    @singledispatchmethod
    def show(self, value):
        return value
