using System.IO;
using System.Runtime.Serialization.Formatters.Binary;
using Newtonsoft.Json;

public class Loader
{
    public object FromStream(Stream input)
    {
        var formatter = new BinaryFormatter();
        return formatter.Deserialize(input);
    }

    public object FromJson(string text)
    {
        var settings = new JsonSerializerSettings { TypeNameHandling = TypeNameHandling.Auto };
        return JsonConvert.DeserializeObject(text, settings);
    }
}
