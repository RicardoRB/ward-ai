class Reports {
    List<String> names(List<Project> projects) {
        final List<String> names =
                projects.stream()
                        .map(Project::name)
                        .filter(name -> !name.isBlank())
                        .collect(Collectors.toMap(Function.identity(), String::length));
        final Map<String, Integer> lengths =
                names.stream()
                        .collect(
                                Collectors.toMap(
                                        Function.identity(),
                                        String::length,
                                        (left, right) -> left,
                                        LinkedHashMap::new
                                )
                        );
        final String label =
                names.isEmpty()
                        ? "none"
                        : String.join(", ", names);
        final Runnable task =
                new Runnable() {
                    @Override
                    public void run() {
                        log(label);
                    }
                };
        final int size =
                switch (names.size()) {
                    case 0 -> 0;
                    default -> 1;
                };
        final String text =
                label
                        + names.stream()
                                .map(String::trim)
                                .collect(Collectors.joining())
                        + size;
        return label
                + "/"
                // the size follows the label
                + size;
    }
}

final class Service
        implements Runnable, AutoCloseable {
    public void run() {}
}
