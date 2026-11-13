import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;

class Mappers {
  void legacy(ObjectMapper mapper) {
    mapper.enableDefaultTyping();
  }

  void open(ObjectMapper mapper) {
    mapper.activateDefaultTyping(LaissezFaireSubTypeValidator.instance);
  }
}
