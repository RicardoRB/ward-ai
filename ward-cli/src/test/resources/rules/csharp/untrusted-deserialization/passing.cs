using System.Text.Json;
using Newtonsoft.Json;

public class Loader
{
    public Order FromJson(string text)
    {
        return JsonSerializer.Deserialize<Order>(text);
    }

    public bool Reads(JsonSerializerSettings settings)
    {
        return settings.TypeNameHandling == TypeNameHandling.Auto || Check(TypeNameHandling.All);
    }

    public Order FromNewtonsoft(string text)
    {
        var settings = new JsonSerializerSettings { TypeNameHandling = TypeNameHandling.None };
        return JsonConvert.DeserializeObject<Order>(text, settings);
    }
}

public class Order
{
    public int Id { get; set; }
}
