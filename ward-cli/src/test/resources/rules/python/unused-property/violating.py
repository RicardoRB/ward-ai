import functools


class Account:
    @property
    def balance(self):
        return 1

    @property
    def fee(self):
        return 2

    @functools.cached_property
    def tax(self):
        return 3

    def report(self):
        return self.balance
