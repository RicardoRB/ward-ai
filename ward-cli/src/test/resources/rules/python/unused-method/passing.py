class Service:
    def used_method(self):
        return 1

    def run(self):
        return self.used_method()

    @staticmethod
    def helper():
        return 2


Service.helper()
Service().run()
