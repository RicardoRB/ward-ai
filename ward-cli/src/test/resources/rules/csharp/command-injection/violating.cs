using System.Diagnostics;
using Microsoft.AspNetCore.Mvc;

public class ToolController : ControllerBase
{
    public void Archive()
    {
        var folder = Request.Query["folder"];
        Process.Start("tar", "-cf backup.tar " + folder);
    }

    public void Convert()
    {
        var file = Request.Query["file"];
        var info = new ProcessStartInfo();
        info.FileName = "convert";
        info.Arguments = file;
        Process.Start(info);
    }

    public void Launch()
    {
        var program = Request.Query["program"];
        var info = new ProcessStartInfo(program);
        Process.Start(info);
    }
}
