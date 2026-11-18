package dev.ward.infrastructure.cli.agent;

import dev.ward.application.agent.NativeHookProtocol;
import dev.ward.domain.agent.NativeHookInput;
import dev.ward.domain.json.JsonCodec;
import dev.ward.domain.json.JsonCodecException;
import dev.ward.domain.secrets.PromptInput;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/** Reads a bounded native hook payload; anything unreadable becomes an unreadable event. */
final class NativeHookEvents {
  private static final int MAX_PAYLOAD_BYTES = 1_048_576;

  private NativeHookEvents() {}

  static NativeHookInput read(
      final InputStream input,
      final NativeHookProtocol protocol,
      final JsonCodec codec
  ) {
    return parse(payload(input), protocol, codec);
  }

  /** Reads the payload text; blank when it is missing, unreadable or over the size bound. */
  static String payload(final InputStream input) {
    try {
      final byte[] bytes = input.readNBytes(MAX_PAYLOAD_BYTES + 1);
      return bytes.length > MAX_PAYLOAD_BYTES ? "" : new String(bytes, StandardCharsets.UTF_8);
    } catch (IOException exception) {
      return "";
    }
  }

  /** Parses the payload text into an event; anything unreadable becomes an unreadable event. */
  static NativeHookInput parse(
      final String payload,
      final NativeHookProtocol protocol,
      final JsonCodec codec
  ) {
    try {
      return payload.isBlank() ? NativeHookInput.unreadableInput() : protocol.parse(codec, payload);
    } catch (JsonCodecException exception) {
      return NativeHookInput.unreadableInput();
    }
  }

  /** Returns the prompt the payload carries; empty for any other event or an unreadable payload. */
  static Optional<PromptInput> prompt(
      final String payload,
      final NativeHookProtocol protocol,
      final JsonCodec codec
  ) {
    try {
      return payload.isBlank() ? Optional.empty() : protocol.parsePrompt(codec, payload);
    } catch (JsonCodecException exception) {
      return Optional.empty();
    }
  }
}
