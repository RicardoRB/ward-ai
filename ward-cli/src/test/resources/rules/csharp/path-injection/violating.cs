using System.IO;
using Microsoft.AspNetCore.Mvc;

public class FileController : ControllerBase
{
    public string Read()
    {
        var name = Request.Query["name"];
        var path = Path.Combine("/srv/files", name);
        return File.ReadAllText(path);
    }

    public void Remove()
    {
        var name = Request.Query["name"];
        File.Delete("/srv/uploads/" + name);
    }

    public Stream Open()
    {
        var name = Request.Query["name"];
        return new FileStream("/srv/data/" + name, FileMode.Open);
    }

    public string Relay()
    {
        var name = Request.Query["name"];
        return Load(Path.Combine("/srv/files", name));
    }

    private string Load(string path)
    {
        return File.ReadAllText(path);
    }
}
