import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;

class Mappers {
  void restricted(ObjectMapper mapper, PolymorphicTypeValidator validator) {
    mapper.activateDefaultTyping(validator);
  }

  // A builder whose enableDefaultTyping takes the validator that lists the allowed types.
  Object builder(TypingBuilder builder, PolymorphicTypeValidator validator) {
    return builder.enableDefaultTyping(validator).build();
  }

  interface TypingBuilder {
    TypingBuilder enableDefaultTyping(PolymorphicTypeValidator validator);

    Object build();
  }
}
