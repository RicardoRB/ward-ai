class Pair:
    def run(self):
        return 1

    def stop(self):
        return 0


class Profile:
    def first(self):
        return 1

    def second(self):
        return 2

    class Meta:
        ordering = ("name",)
