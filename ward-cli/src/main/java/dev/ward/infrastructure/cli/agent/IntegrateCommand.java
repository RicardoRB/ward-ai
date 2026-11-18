package dev.ward.infrastructure.cli.agent;

import dev.ward.application.agent.IntegrationService;
import dev.ward.application.init.PromptTableEnabler;
import dev.ward.domain.agent.IntegrationRequest;
import dev.ward.domain.agent.IntegrationResult;
import dev.ward.domain.agent.SupportedAgents;
import dev.ward.domain.config.WardException;
import dev.ward.infrastructure.cli.ExitCodes;
import dev.ward.infrastructure.cli.ProjectOptions;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Callable;
import javax.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/** The {@code ward integrate} command: installs or removes an agent integration. */
@Command(
    name = "integrate",
    description = "Configure a supported coding-agent integration.",
    mixinStandardHelpOptions = true,
    subcommands = {}
)
public final class IntegrateCommand implements Callable<Integer> {
  @Spec private CommandSpec spec;

  // Picocli assigns annotated fields reflectively, so they cannot be final.
  @Mixin private final ProjectOptions options = new ProjectOptions();

  @Parameters(
      index = "0",
      arity = "0..1",
      description = "Supported agent ID (prompts when omitted)"
  )
  private String agent;

  @Option(names = "--remove", description = "Remove only Ward-owned integration content.")
  private boolean remove;

  @Option(
      names = "--prompt-hook",
      description =
          "Also register the hook that blocks a prompt containing a secret, when the agent has a"
              + " prompt event that can reject a prompt."
  )
  private boolean promptHook;

  private final IntegrationService service;
  private final InputStream input;
  private final PromptTableEnabler prompts;

  /** Creates the command reading confirmations from standard input. */
  @Inject
  public IntegrateCommand(final IntegrationService service, final PromptTableEnabler prompts) {
    this(service, System.in, prompts);
  }

  /** Creates the command reading confirmations from {@code input}, enabling no prompt table. */
  public IntegrateCommand(final IntegrationService service, final InputStream input) {
    this(service, input, PromptTableEnabler.NONE);
  }

  /** Creates the command with every collaborator. */
  public IntegrateCommand(
      final IntegrationService service,
      final InputStream input,
      final PromptTableEnabler prompts
  ) {
    this.service = service;
    this.input = input;
    this.prompts = prompts;
  }

  @Override
  public Integer call() {
    if (agent == null || agent.isBlank()) {
      agent = selectAgent();
    }
    final IntegrationResult result =
        service.integrate(
            agent,
            options.directory(),
            new IntegrationRequest(remove).withPromptHook(promptHook)
        );
    output().printf("%s: %s%n", result.status(), result.message());
    if (!result.capabilities().isEmpty()) {
      output()
          .printf(
              "Capabilities: %s%n",
              result.capabilities().stream().map(Enum::name).sorted().toList()
          );
    }
    result.changed().forEach(output()::println);
    if (promptHook && !remove && result.status() != IntegrationResult.Status.CONFLICT) {
      reportPromptHook();
    }
    return result.status() == IntegrationResult.Status.CONFLICT ? ExitCodes.ERROR : ExitCodes.OK;
  }

  /** Enables the prompt table for an agent with a prompt hook; names an agent without one. */
  private void reportPromptHook() {
    if (!SupportedAgents.promptHookIds().contains(agent)) {
      output().printf("Prompt hook is not supported for %s; none was installed.%n", agent);
      return;
    }
    prompts.enable(options.directory()).ifPresent(output()::println);
  }

  private String selectAgent() {
    output().println("Select an agent integration:");
    final List<SupportedAgents.Definition> agents = SupportedAgents.definitions();
    for (int index = 0; index < agents.size(); index++) {
      output().printf("%d) %s%n", index + 1, agents.get(index).id());
    }
    output().printf("Choose an agent [1-%d]: ", agents.size());
    output().flush();
    try {
      final String selection =
          new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8)).readLine();
      if (selection == null) {
        throw new WardException(
            "No agent selected. Pass an agent argument or choose one interactively."
        );
      }
      final int selected = parseSelection(selection, agents.size());
      if (selected < 1 || selected > agents.size()) {
        throw invalidSelection(selection, agents.size());
      }
      return agents.get(selected - 1).id();
    } catch (IOException e) {
      throw new WardException("Could not read agent selection: " + e.getMessage(), e);
    }
  }

  private static int parseSelection(final String selection, final int size) {
    try {
      return Integer.parseInt(selection.trim());
    } catch (NumberFormatException exception) {
      throw invalidSelection(selection, size, exception);
    }
  }

  private static WardException invalidSelection(final String selection, final int size) {
    return invalidSelection(selection, size, null);
  }

  private static WardException invalidSelection(
      final String selection,
      final int size,
      final Throwable cause
  ) {
    return new WardException(
        "Invalid agent selection '" + selection + "'. Choose a number from 1 to " + size + ".",
        cause
    );
  }

  private PrintWriter output() {
    return spec == null
        ? new PrintWriter(System.out, true, StandardCharsets.UTF_8)
        : spec.commandLine().getOut();
  }
}
