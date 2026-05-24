using Katas.CustomLinq;

namespace Katas.Tests.CustomLinq;

public class SequenceOperatorsTests
{
    // =========================================================================
    // Window
    // =========================================================================

    [Fact]
    public void Window_Should_ProduceOverlappingWindowsOfRequestedSize()
    {
        int[] source = [1, 2, 3, 4, 5];
        var windows = source.Window(3).ToList();

        Assert.Equal(3, windows.Count);
        Assert.Equal([1, 2, 3], windows[0]);
        Assert.Equal([2, 3, 4], windows[1]);
        Assert.Equal([3, 4, 5], windows[2]);
    }

    [Fact]
    public void Window_Should_YieldNothingWhenSourceSmallerThanSize()
    {
        int[] source = [1, 2];
        var windows = source.Window(5).ToList();
        Assert.Empty(windows);
    }

    [Fact]
    public void Window_Should_YieldSingleWindowWhenSourceExactlySize()
    {
        int[] source = [10, 20, 30];
        var windows = source.Window(3).ToList();
        Assert.Single(windows);
        Assert.Equal([10, 20, 30], windows[0]);
    }

    [Fact]
    public void Window_Should_YieldNothingForEmptySource()
    {
        var windows = Array.Empty<int>().Window(2).ToList();
        Assert.Empty(windows);
    }

    [Fact]
    public void Window_Should_YieldSingleElementWindowsForSizeOne()
    {
        int[] source = [7, 8, 9];
        var windows = source.Window(1).ToList();
        Assert.Equal(3, windows.Count);
        Assert.Equal([7], windows[0]);
        Assert.Equal([8], windows[1]);
        Assert.Equal([9], windows[2]);
    }

    [Fact]
    public void Window_Should_ThrowArgumentOutOfRangeForZeroSize()
    {
        Assert.Throws<ArgumentOutOfRangeException>(() => Array.Empty<int>().Window(0).ToList());
    }

    [Fact]
    public void Window_Should_ThrowArgumentOutOfRangeForNegativeSize()
    {
        Assert.Throws<ArgumentOutOfRangeException>(() => Array.Empty<int>().Window(-1).ToList());
    }

    [Fact]
    public void Window_Should_ThrowArgumentNullExceptionForNullSource()
    {
        IEnumerable<int> nullSource = null!;
        Assert.Throws<ArgumentNullException>(() => nullSource.Window(3));
    }

    [Fact]
    public void Window_Should_BeDeferredAndNotEnumerateSourceUntilIterated()
    {
        // Arrange: build a result but do NOT iterate it yet.
        var enumerated = false;
        IEnumerable<int> LazySource()
        {
            enumerated = true;
            yield return 1;
            yield return 2;
            yield return 3;
        }

        var result = LazySource().Window(2); // no iteration here

        Assert.False(enumerated, "Source was enumerated at operator-creation time — must be deferred.");

        _ = result.ToList(); // now iterate

        Assert.True(enumerated, "Source should be enumerated after iteration.");
    }

    [Fact]
    public void Window_Should_ReturnImmutableSnapshotsNotSharedBuffers()
    {
        // If the implementation reuses the same buffer object, all windows
        // would alias the same array and callers keeping references would see
        // stale/overwritten data.
        int[] source = [1, 2, 3, 4];
        var windows = source.Window(2).ToList();

        // Each list must be a distinct object with independent content.
        Assert.NotSame(windows[0], windows[1]);
        Assert.Equal([1, 2], windows[0]);
        Assert.Equal([2, 3], windows[1]);
        Assert.Equal([3, 4], windows[2]);
    }

    // =========================================================================
    // Batch
    // =========================================================================

    [Fact]
    public void Batch_Should_PartitionEvenlyDivisibleSequence()
    {
        int[] source = [1, 2, 3, 4, 5, 6];
        var batches = source.Batch(2).ToList();

        Assert.Equal(3, batches.Count);
        Assert.Equal([1, 2], batches[0]);
        Assert.Equal([3, 4], batches[1]);
        Assert.Equal([5, 6], batches[2]);
    }

    [Fact]
    public void Batch_Should_IncludeRemainingElementsInSmallerFinalBatch()
    {
        int[] source = [1, 2, 3, 4, 5];
        var batches = source.Batch(3).ToList();

        Assert.Equal(2, batches.Count);
        Assert.Equal([1, 2, 3], batches[0]);
        Assert.Equal([4, 5], batches[1]);  // smaller final batch
    }

    [Fact]
    public void Batch_Should_YieldNothingForEmptySource()
    {
        var batches = Array.Empty<int>().Batch(3).ToList();
        Assert.Empty(batches);
    }

    [Fact]
    public void Batch_Should_YieldSingleBatchWhenSourceSmallerThanSize()
    {
        int[] source = [1, 2];
        var batches = source.Batch(10).ToList();
        Assert.Single(batches);
        Assert.Equal([1, 2], batches[0]);
    }

    [Fact]
    public void Batch_Should_YieldSingleElementBatchesForSizeOne()
    {
        int[] source = [4, 5, 6];
        var batches = source.Batch(1).ToList();
        Assert.Equal(3, batches.Count);
        Assert.Equal([4], batches[0]);
        Assert.Equal([5], batches[1]);
        Assert.Equal([6], batches[2]);
    }

    [Fact]
    public void Batch_Should_ThrowArgumentNullExceptionForNullSource()
    {
        IEnumerable<int> nullSource = null!;
        Assert.Throws<ArgumentNullException>(() => nullSource.Batch(3));
    }

    [Fact]
    public void Batch_Should_ThrowArgumentOutOfRangeForZeroSize()
    {
        Assert.Throws<ArgumentOutOfRangeException>(() => Array.Empty<int>().Batch(0).ToList());
    }

    [Fact]
    public void Batch_Should_BeDeferredAndNotEnumerateSourceUntilIterated()
    {
        var enumerated = false;
        IEnumerable<int> LazySource()
        {
            enumerated = true;
            yield return 1;
        }

        var result = LazySource().Batch(2);

        Assert.False(enumerated, "Source must not be enumerated at operator-creation time.");
        _ = result.ToList();
        Assert.True(enumerated);
    }

    // =========================================================================
    // Scan
    // =========================================================================

    [Fact]
    public void Scan_Should_EmitRunningSum()
    {
        int[] source = [1, 2, 3, 4, 5];
        var running = source.Scan(0, (acc, x) => acc + x).ToList();

        // Seed is 0; after each element: 1, 3, 6, 10, 15
        Assert.Equal([1, 3, 6, 10, 15], running);
    }

    [Fact]
    public void Scan_Should_NotEmitSeedItself()
    {
        int[] source = [10];
        var result = source.Scan(999, (acc, x) => acc + x).ToList();

        // Only one element; result should be [999 + 10 = 1009], NOT [999, 1009]
        Assert.Single(result);
        Assert.Equal(1009, result[0]);
    }

    [Fact]
    public void Scan_Should_YieldNothingForEmptySource()
    {
        var result = Array.Empty<int>().Scan(0, (acc, x) => acc + x).ToList();
        Assert.Empty(result);
    }

    [Fact]
    public void Scan_Should_WorkWithStringConcatenation()
    {
        string[] words = ["Hello", " ", "World"];
        var scanned = words.Scan("", (acc, w) => acc + w).ToList();

        Assert.Equal(["Hello", "Hello ", "Hello World"], scanned);
    }

    [Fact]
    public void Scan_Should_ThrowArgumentNullExceptionForNullSource()
    {
        IEnumerable<int> nullSource = null!;
        Assert.Throws<ArgumentNullException>(() => nullSource.Scan(0, (a, x) => a + x));
    }

    [Fact]
    public void Scan_Should_ThrowArgumentNullExceptionForNullFolder()
    {
        Func<int, int, int> nullFolder = null!;
        Assert.Throws<ArgumentNullException>(() => new[] { 1 }.Scan(0, nullFolder));
    }

    [Fact]
    public void Scan_Should_BeDeferredAndNotEnumerateSourceUntilIterated()
    {
        var enumerated = false;
        IEnumerable<int> LazySource()
        {
            enumerated = true;
            yield return 1;
        }

        var result = LazySource().Scan(0, (a, x) => a + x);

        Assert.False(enumerated, "Source must not be enumerated at operator-creation time.");
        _ = result.ToList();
        Assert.True(enumerated);
    }

    // =========================================================================
    // Pairwise
    // =========================================================================

    [Fact]
    public void Pairwise_Should_ProduceConsecutivePairs()
    {
        int[] source = [1, 2, 3, 4];
        var pairs = source.Pairwise().ToList();

        Assert.Equal(3, pairs.Count);
        Assert.Equal((1, 2), pairs[0]);
        Assert.Equal((2, 3), pairs[1]);
        Assert.Equal((3, 4), pairs[2]);
    }

    [Fact]
    public void Pairwise_Should_YieldNothingForEmptySource()
    {
        var pairs = Array.Empty<int>().Pairwise().ToList();
        Assert.Empty(pairs);
    }

    [Fact]
    public void Pairwise_Should_YieldNothingForSingleElementSource()
    {
        var pairs = new[] { 42 }.Pairwise().ToList();
        Assert.Empty(pairs);
    }

    [Fact]
    public void Pairwise_Should_YieldOnePairForTwoElementSource()
    {
        var pairs = new[] { 10, 20 }.Pairwise().ToList();
        Assert.Single(pairs);
        Assert.Equal((10, 20), pairs[0]);
    }

    [Fact]
    public void Pairwise_Should_ThrowArgumentNullExceptionForNullSource()
    {
        IEnumerable<int> nullSource = null!;
        Assert.Throws<ArgumentNullException>(() => nullSource.Pairwise());
    }

    [Fact]
    public void Pairwise_Should_BeDeferredAndNotEnumerateSourceUntilIterated()
    {
        var enumerated = false;
        IEnumerable<int> LazySource()
        {
            enumerated = true;
            yield return 1;
            yield return 2;
        }

        var result = LazySource().Pairwise();

        Assert.False(enumerated, "Source must not be enumerated at operator-creation time.");
        _ = result.ToList();
        Assert.True(enumerated);
    }

    [Fact]
    public void Pairwise_Should_NamedTupleFieldsBeAccessible()
    {
        var pair = new[] { 5, 10 }.Pairwise().Single();
        Assert.Equal(5, pair.Previous);
        Assert.Equal(10, pair.Current);
    }
}
