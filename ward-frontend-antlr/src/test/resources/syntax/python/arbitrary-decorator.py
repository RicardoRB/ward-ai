buttons = []

@buttons[0].clicked.connect
def on_click():
    pass

@(lambda fn: fn)
def wrapped():
    pass
