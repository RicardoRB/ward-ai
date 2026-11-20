using System.IO;
using Microsoft.AspNetCore.Mvc;

public class FileController : ControllerBase
{
    public string Read()
    {
        var name = Path.GetFileName(Request.Query["name"]);
        var path = Path.Combine("/srv/files", name);
        return File.ReadAllText(path);
    }

    public string Settings()
    {
        return File.ReadAllText("/srv/files/settings.json");
    }

    public void Remove()
    {
        var age = int.Parse(Request.Query["age"]);
        File.Delete("/var/cache/entry-" + age);
    }

    public void Show()
    {
        var name = Request.Query["name"];
        System.Console.WriteLine(name);
    }
}
