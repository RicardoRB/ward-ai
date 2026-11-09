fun run(repo: Repo) {
    val kept = repo.save("x")
    repo.size()
}
