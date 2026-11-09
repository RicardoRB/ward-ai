class Config:
    def __init__(self):
        self.host = "localhost"
        self.port = 8080
        self.debug = False

    def address(self):
        return self.host
