using System;
using System.Collections.Generic;
using static System.Math;
using Alias = System.Collections.Generic.List<int>;
global using System.Text;

namespace Demo.Classes
{
    [Serializable]
    public abstract partial class Base<T, U> : IDisposable, IComparable<Base<T, U>>
        where T : class, IDisposable, new()
        where U : struct
    {
        private const int Limit = 10;
        protected static readonly object Gate = new();
        public event EventHandler? Changed;
        public delegate void Notify<in TArg>(TArg arg);

        public required string Id { get; init; }
        public int this[int index] { get => index; set { } }

        static Base() { }
        protected Base() : this(1) { }
        protected Base(int value) { }
        ~Base() { }

        public abstract T Create<V>(V seed) where V : notnull;

        public static Base<T, U> operator +(Base<T, U> left, Base<T, U> right) => left;
        public static implicit operator int(Base<T, U> value) => 0;
        public static explicit operator Base<T, U>(int value) => null!;

        public virtual void Dispose()
        {
            Changed?.Invoke(this, EventArgs.Empty);
            GC.SuppressFinalize(this);
        }

        public int CompareTo(Base<T, U>? other) => 0;
    }

    public enum Level : byte
    {
        Low = 1,
        High = 2,
    }

    public interface IShape
    {
        double Area { get; }
    }
}
