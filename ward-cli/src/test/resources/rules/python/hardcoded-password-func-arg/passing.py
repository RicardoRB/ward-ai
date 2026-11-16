connect(host="db")
# The text of a string is not a keyword argument.
run('CREATE VIRTUAL TABLE notes USING fts5(title, tokenize="porter")')
