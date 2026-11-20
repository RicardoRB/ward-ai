using System.Diagnostics;
using Microsoft.AspNetCore.Mvc;

public class ToolController : ControllerBase
{
    public void Archive()
    {
        var level = int.Parse(Request.Query["level"]);
        Process.Start("gzip", "-" + level + " backup.tar");
    }

    public void Fixed()
    {
        var info = new ProcessStartInfo();
        info.FileName = "git";
        info.Arguments = "status";
        Process.Start(info);
    }

    public void Show()
    {
        var folder = Request.Query["folder"];
        System.Console.WriteLine(folder);
    }
}
