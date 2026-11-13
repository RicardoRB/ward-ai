import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;

class Mappers {
  void restricted(ObjectMapper mapper, PolymorphicTypeValidator validator) {
    mapper.activateDefaultTyping(validator);
  }
}
