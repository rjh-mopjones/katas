namespace Katas.Tests.Generics;

using Katas.Generics;

public sealed class ResultTests
{
    // -------------------------------------------------------------------------
    // Ok / IsOk
    // -------------------------------------------------------------------------

    [Fact]
    public void Ok_Should_ReportIsOkTrue()
    {
        Result<int, string> result = Result<int, string>.Ok(1);
        Assert.True(result.IsOk);
    }

    [Fact]
    public void Err_Should_ReportIsOkFalse()
    {
        Result<int, string> result = Result<int, string>.Err("bad input");
        Assert.False(result.IsOk);
    }

    // -------------------------------------------------------------------------
    // Map
    // -------------------------------------------------------------------------

    [Fact]
    public void Map_Should_TransformSuccessValue_WhenOk()
    {
        Result<int, string> result = Result<int, string>.Ok(4);
        Result<string, string> mapped = result.Map(n => n.ToString());
        Assert.True(mapped.IsOk);
        Assert.Equal("4", mapped.Match(v => v, _ => ""));
    }

    [Fact]
    public void Map_Should_PropagateError_WhenErr()
    {
        Result<int, string> result = Result<int, string>.Err("oops");
        Result<string, string> mapped = result.Map(n => n.ToString());
        Assert.False(mapped.IsOk);
        Assert.Equal("oops", mapped.Match(_ => "", e => e));
    }

    // -------------------------------------------------------------------------
    // Bind
    // -------------------------------------------------------------------------

    [Fact]
    public void Bind_Should_ChainOperations_WhenBothOk()
    {
        Result<int, string> r = Result<int, string>.Ok(10);
        Result<int, string> doubled = r.Bind(n => n > 0
            ? Result<int, string>.Ok(n * 2)
            : Result<int, string>.Err("not positive"));
        Assert.True(doubled.IsOk);
        Assert.Equal(20, doubled.Match(v => v, _ => -1));
    }

    [Fact]
    public void Bind_Should_PropagateFirstError_WhenBinderReturnsErr()
    {
        Result<int, string> r = Result<int, string>.Ok(-1);
        Result<int, string> result = r.Bind(n => n > 0
            ? Result<int, string>.Ok(n * 2)
            : Result<int, string>.Err("not positive"));
        Assert.False(result.IsOk);
        Assert.Equal("not positive", result.Match(_ => "", e => e));
    }

    [Fact]
    public void Bind_Should_SkipBinder_WhenOriginalIsErr()
    {
        Result<int, string> r = Result<int, string>.Err("original error");
        bool binderCalled = false;
        Result<int, string> result = r.Bind(n => { binderCalled = true; return Result<int, string>.Ok(n); });
        Assert.False(result.IsOk);
        Assert.False(binderCalled);
    }

    // -------------------------------------------------------------------------
    // Match
    // -------------------------------------------------------------------------

    [Fact]
    public void Match_Should_CallOkBranch_WhenOk()
    {
        Result<int, string> result = Result<int, string>.Ok(99);
        string s = result.Match(v => $"ok:{v}", e => $"err:{e}");
        Assert.Equal("ok:99", s);
    }

    [Fact]
    public void Match_Should_CallErrBranch_WhenErr()
    {
        Result<int, string> result = Result<int, string>.Err("fail");
        string s = result.Match(v => $"ok:{v}", e => $"err:{e}");
        Assert.Equal("err:fail", s);
    }
}
