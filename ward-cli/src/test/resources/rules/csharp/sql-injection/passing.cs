using System.Data.SqlClient;
using Microsoft.AspNetCore.Mvc;

public class OrderController : ControllerBase
{
    public string Find(SqlConnection connection)
    {
        var customer = Request.Query["customer"];
        var command = new SqlCommand("SELECT * FROM orders WHERE customer = @customer", connection);
        command.Parameters.AddWithValue("@customer", customer);
        return command.ExecuteScalar().ToString();
    }

    public string ById(SqlConnection connection)
    {
        var id = int.Parse(Request.Query["id"]);
        var command = new SqlCommand("SELECT * FROM orders WHERE id = " + id, connection);
        return command.ExecuteScalar().ToString();
    }

    public string Fixed(SqlConnection connection)
    {
        var command = new SqlCommand("SELECT COUNT(*) FROM orders", connection);
        return command.ExecuteScalar().ToString();
    }

    public void Show()
    {
        var customer = Request.Query["customer"];
        System.Console.WriteLine(customer);
    }
}
