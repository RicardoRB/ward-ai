using System.Data.SqlClient;
using Microsoft.AspNetCore.Mvc;

public class OrderController : ControllerBase
{
    public string Find(SqlConnection connection)
    {
        var customer = Request.Query["customer"];
        var command = new SqlCommand("SELECT * FROM orders WHERE customer = '" + customer + "'", connection);
        return command.ExecuteScalar().ToString();
    }

    public string Count(HttpRequest request, SqlConnection connection)
    {
        string status = request.Form["status"];
        var command = new SqlCommand();
        command.CommandText = $"SELECT COUNT(*) FROM orders WHERE status = '{status}'";
        command.Connection = connection;
        return command.ExecuteScalar().ToString();
    }

    public string Relay(SqlConnection connection)
    {
        var customer = Request.Query["customer"];
        return Execute(customer, connection);
    }

    private string Execute(string text, SqlConnection connection)
    {
        var command = new SqlCommand(text, connection);
        return command.ExecuteScalar().ToString();
    }
}
