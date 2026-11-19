using System;
using System.Collections.Generic;
using System.Threading;
using System.Threading.Tasks;

namespace Demo.Streams;

public class Producer
{
    public async IAsyncEnumerable<int> CountAsync(int limit, [System.Runtime.CompilerServices.EnumeratorCancellation] CancellationToken token = default)
    {
        for (var i = 0; i < limit; i++)
        {
            await Task.Delay(1, token);
            yield return i;
        }
        yield break;
    }

    public async Task<int> SumAsync()
    {
        var total = 0;
        await foreach (var value in CountAsync(5))
        {
            total += value;
        }
        await using var resource = new AsyncResource();
        await using (var other = new AsyncResource())
        {
            total++;
        }
        return total;
    }
}

public sealed class AsyncResource : IAsyncDisposable
{
    public ValueTask DisposeAsync() => ValueTask.CompletedTask;
}
