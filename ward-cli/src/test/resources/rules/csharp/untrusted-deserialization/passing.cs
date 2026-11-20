using System.Text.Json;
using Newtonsoft.Json;

public class Loader
{
    public Order FromJson(string text)
    {
        return JsonSerializer.Deserialize<Order>(text);
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
