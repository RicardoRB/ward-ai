from functools import singledispatch


class Printer:
    @singledispatch
    def show(self, value):
        return value
