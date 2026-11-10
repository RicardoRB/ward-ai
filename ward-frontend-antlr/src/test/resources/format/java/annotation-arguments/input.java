@Command(name = "format", description = "Format the project's source files in Ward style.", mixinStandardHelpOptions = true)
@Component(modules = {ProjectBindingsModule.class, FormatterBindingsModule.class, ReportBindingsModule.class})
class FormatCommand {
    @Option(names = "--check", description = "Write nothing.")
    boolean check;
    @SuppressWarnings({"a", "b"})
    void run() {}
}
