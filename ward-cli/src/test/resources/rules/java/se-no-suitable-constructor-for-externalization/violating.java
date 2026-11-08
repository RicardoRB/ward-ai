import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

final class Check implements Externalizable {
  Check(int x) {}

  public void writeExternal(ObjectOutput out) throws IOException {}

  public void readExternal(ObjectInput in) throws IOException {}
}
