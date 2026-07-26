import pytest

from . import FileSystem, FileSystemError


def test_mkdir_p_then_ls_sorted():
    fs = FileSystem()
    fs.mkdir("/a/b/c")
    fs.mkdir("/a/x")
    assert fs.ls("/a") == ["b", "x"]  # sorted child names
    assert fs.ls("/a/b") == ["c"]


def test_write_then_read_round_trip():
    fs = FileSystem()
    fs.mkdir("/docs")
    fs.write("/docs/hello.txt", "hi there")
    assert fs.read("/docs/hello.txt") == "hi there"


def test_write_overwrites_existing_file():
    fs = FileSystem()
    fs.write("/note", "first")
    fs.write("/note", "second")
    assert fs.read("/note") == "second"


def test_nested_dirs_and_files():
    fs = FileSystem()
    fs.mkdir("/a/b")
    fs.write("/a/b/f1", "one")
    fs.write("/a/f2", "two")
    assert fs.ls("/a") == ["b", "f2"]
    assert fs.ls("/a/b") == ["f1"]


def test_read_missing_path_raises():
    fs = FileSystem()
    with pytest.raises(FileSystemError):
        fs.read("/nope")


def test_read_of_directory_raises():
    fs = FileSystem()
    fs.mkdir("/d")
    with pytest.raises(FileSystemError):
        fs.read("/d")


def test_write_to_missing_parent_raises():
    fs = FileSystem()
    with pytest.raises(FileSystemError):
        fs.write("/missing/dir/file", "x")


def test_write_where_dir_exists_raises():
    fs = FileSystem()
    fs.mkdir("/a")
    with pytest.raises(FileSystemError):
        fs.write("/a", "x")


def test_ls_of_file_returns_basename():
    fs = FileSystem()
    fs.mkdir("/a")
    fs.write("/a/file.txt", "x")
    assert fs.ls("/a/file.txt") == ["file.txt"]


def test_ls_missing_path_raises():
    fs = FileSystem()
    with pytest.raises(FileSystemError):
        fs.ls("/nope")


def test_find_by_basename_across_tree_sorted():
    fs = FileSystem()
    fs.mkdir("/a/target")
    fs.mkdir("/b")
    fs.write("/b/target", "x")
    fs.write("/a/other", "y")
    assert fs.find("target") == ["/a/target", "/b/target"]
    assert fs.find("nothing") == []


def test_mv_a_file():
    fs = FileSystem()
    fs.mkdir("/a")
    fs.mkdir("/b")
    fs.write("/a/f", "content")
    fs.mv("/a/f", "/b/g")
    assert "/a/f" not in fs
    assert fs.read("/b/g") == "content"


def test_mv_a_directory_subtree():
    fs = FileSystem()
    fs.mkdir("/a/sub")
    fs.write("/a/sub/deep", "payload")
    fs.mkdir("/dest")
    fs.mv("/a", "/dest/a")
    assert "/a" not in fs
    assert fs.read("/dest/a/sub/deep") == "payload"  # children came along
    assert fs.ls("/dest/a") == ["sub"]


def test_mv_missing_src_raises():
    fs = FileSystem()
    fs.mkdir("/dst")
    with pytest.raises(FileSystemError):
        fs.mv("/nope", "/dst/x")


def test_mv_missing_dst_parent_raises():
    fs = FileSystem()
    fs.write("/f", "x")
    with pytest.raises(FileSystemError):
        fs.mv("/f", "/missing/g")


def test_contains():
    fs = FileSystem()
    fs.mkdir("/a")
    fs.write("/a/f", "x")
    assert "/a" in fs
    assert "/a/f" in fs
    assert "/a/nope" not in fs


def test_mkdir_over_existing_file_raises():
    fs = FileSystem()
    fs.write("/f", "x")
    with pytest.raises(FileSystemError):
        fs.mkdir("/f/sub")


def test_mkdir_existing_dir_is_noop():
    fs = FileSystem()
    fs.mkdir("/a/b")
    fs.write("/a/b/keep", "x")
    fs.mkdir("/a/b")  # no-op, must not clobber children
    assert fs.read("/a/b/keep") == "x"
