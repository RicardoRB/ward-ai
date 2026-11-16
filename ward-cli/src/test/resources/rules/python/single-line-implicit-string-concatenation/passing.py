text = "first" + "second"
words = text.split(" ")
label = "Error: " + " ".join(words)
clean = text.replace("\xa0", " ")
blank = " "
