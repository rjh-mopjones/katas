namespace Katas.Tests.Generics;

using Katas.Generics;

public sealed class OptionTests
{
    // -------------------------------------------------------------------------
    // Some / IsSome
    // -------------------------------------------------------------------------

    [Fact]
    public void Some_Should_WrapValue_AndReportIsSomeTrue()
    {
        Option<int> opt = Option<int>.Some(42);
        Assert.True(opt.IsSome);
    }

    [Fact]
    public void None_Should_ReportIsSomeFalse()
    {
        Option<int> opt = Option<int>.None;
        Assert.False(opt.IsSome);
    }

    // -------------------------------------------------------------------------
    // Map
    // -------------------------------------------------------------------------

    [Fact]
    public void Map_Should_TransformValue_WhenSome()
    {
        Option<int> opt = Option<int>.Some(5);
        Option<string> result = opt.Map(n => n.ToString());
        Assert.True(result.IsSome);
        Assert.Equal("5", result.GetValueOr(""));
    }

    [Fact]
    public void Map_Should_ReturnNone_WhenNone()
    {
        Option<int> opt = Option<int>.None;
        Option<string> result = opt.Map(n => n.ToString());
        Assert.False(result.IsSome);
    }

    // -------------------------------------------------------------------------
    // Bind
    // -------------------------------------------------------------------------

    [Fact]
    public void Bind_Should_ChainOptions_WhenBothSome()
    {
        Option<int> opt = Option<int>.Some(10);
        Option<int> result = opt.Bind(n => n > 0 ? Option<int>.Some(n * 2) : Option<int>.None);
        Assert.True(result.IsSome);
        Assert.Equal(20, result.GetValueOr(0));
    }

    [Fact]
    public void Bind_Should_ReturnNone_WhenBinderReturnsNone()
    {
        Option<int> opt = Option<int>.Some(-1);
        Option<int> result = opt.Bind(n => n > 0 ? Option<int>.Some(n * 2) : Option<int>.None);
        Assert.False(result.IsSome);
    }

    [Fact]
    public void Bind_Should_ReturnNone_WhenOriginalIsNone()
    {
        Option<int> opt = Option<int>.None;
        bool binderCalled = false;
        Option<int> result = opt.Bind(n => { binderCalled = true; return Option<int>.Some(n); });
        Assert.False(result.IsSome);
        Assert.False(binderCalled);
    }

    // -------------------------------------------------------------------------
    // Match
    // -------------------------------------------------------------------------

    [Fact]
    public void Match_Should_CallSomeBranch_WhenSome()
    {
        Option<int> opt = Option<int>.Some(7);
        string result = opt.Match(n => $"value={n}", () => "empty");
        Assert.Equal("value=7", result);
    }

    [Fact]
    public void Match_Should_CallNoneBranch_WhenNone()
    {
        Option<int> opt = Option<int>.None;
        string result = opt.Match(n => $"value={n}", () => "empty");
        Assert.Equal("empty", result);
    }

    // -------------------------------------------------------------------------
    // GetValueOr
    // -------------------------------------------------------------------------

    [Fact]
    public void GetValueOr_Should_ReturnValue_WhenSome()
    {
        Option<int> opt = Option<int>.Some(3);
        Assert.Equal(3, opt.GetValueOr(99));
    }

    [Fact]
    public void GetValueOr_Should_ReturnFallback_WhenNone()
    {
        Option<int> opt = Option<int>.None;
        Assert.Equal(99, opt.GetValueOr(99));
    }

    // -------------------------------------------------------------------------
    // Monadic laws (sanity check)
    // -------------------------------------------------------------------------

    [Fact]
    public void Bind_Should_SatisfyLeftIdentityLaw()
    {
        // left identity: Some(x).Bind(f) == f(x)
        int x = 5;
        Func<int, Option<string>> f = n => Option<string>.Some(n.ToString());
        Assert.Equal(f(x).GetValueOr(""), Option<int>.Some(x).Bind(f).GetValueOr(""));
    }

    [Fact]
    public void Bind_Should_SatisfyRightIdentityLaw()
    {
        // right identity: m.Bind(Some) == m
        Option<int> m = Option<int>.Some(42);
        Option<int> result = m.Bind(Option<int>.Some);
        Assert.Equal(m.GetValueOr(-1), result.GetValueOr(-1));
    }
}
