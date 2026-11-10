class Service:
    def used_method(self):
        return 1

    def dead_method(self):
        return 2

    def run(self):
        return self.used_method()

    def __repr__(self):
        return "service"

    @staticmethod
    def static_dead():
        return 3

    @property
    def size(self):
        return 4


Service().run()
