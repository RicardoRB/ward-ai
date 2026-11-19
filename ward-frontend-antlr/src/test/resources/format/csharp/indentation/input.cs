using System;
   using System.Linq;
namespace Shop {
public class Cart{
        private readonly   List<int> items=new List<int>();
  public int Count{get{return items.Count;}}
public void Add(int item)
  {
if(item<0){throw new ArgumentException("negative");}
        items.Add(item);
   }
    public int Total()
{
var total=0;
foreach(var i in items)
{
total+=i;
}
return total;
}
}
}
