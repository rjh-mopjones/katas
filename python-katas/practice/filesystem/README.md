# In-Memory File System

> Model directories and files in memory with path-based operations — `mkdir -p`, read/write, `ls`, `find`, and `mv` a whole subtree — the way a real file system does.

## The problem

Build the engine behind an in-memory file system. Directories nest to form a tree and files hold
string content; every operation names a node by an absolute `/`-separated path (`"/a/b/c"`, with
`"/"` the root). Creating a directory should also create any missing parents (`mkdir -p`); writing
a file needs its parent directory to exist; `find` locates every node with a given name anywhere in
the tree; and moving a directory must carry its whole subtree along.

## Requirements

- Paths are absolute and `/`-separated; `"/"` is the root.
- `mkdir(path)` creates the directory and every missing intermediate directory. Making an existing
  directory is a no-op; making a path that is an existing **file** raises `FileSystemError`.
- `write(path, content)` requires the parent directory to exist (else raise); it overwrites an
  existing file; writing where a **directory** exists raises.
- `read(path)` returns the file's content; a missing path or a directory raises.
- `ls(path)` returns a directory's child names **sorted**; for a file, its basename in a list; a
  missing path raises.
- `find(name)` returns every path in the tree whose final component equals `name`, **sorted**.
- `mv(src, dst)` moves a node (file or directory subtree) from `src` to `dst`; a missing `src` or a
  missing `dst` parent raises.
- `path in fs` reports whether the path exists.

## What you implement

- `FileSystem.mkdir`, `write`, `read`, `ls`, `find`, `mv`, and `__contains__` — the signatures in
  `__init__.py`.

`FileSystemError` is provided. You design the storage and path resolution.

## The real challenge

- **Model the tree, not a pile of paths.** A directory holds a `dict[str, Node]` mapping each
  child's *name* to its node; a file holds content. A small `dataclass` (or a nested dict — your
  choice) makes each node explicit. This is the shape every operation walks.
- **Path resolution is a recursive descent.** Split the path into components and walk from the root
  one component at a time. Every failure mode lives here: a missing intermediate, or an intermediate
  that is a file rather than a directory. Write resolution once and let `read`/`ls`/`mv`/`write`
  share it.
- **`find` and subtree-`mv` recurse over the tree.** `find` is a depth-first walk collecting
  matching basenames. `mv` of a directory is "free": because a directory node *is* the root of its
  subtree, re-parenting the one node moves everything beneath it — no per-child copying.
- **Normalise paths carefully.** `"/a/b"` → `["a", "b"]`, `"/"` → `[]`; decide what an absolute path
  requires and reject the rest.
- **`__contains__` is the idiomatic membership check.** `"/a/b" in fs` is what path existence should
  read like — implement the dunder, not an `exists()` method.
- **Tree vs. flat dict — know the trade-off.** You could back the whole thing with a flat
  `dict[str, str]` keyed by full path: `read`/`write` become one lookup, but `ls` must scan every
  key for a shared prefix and subtree-`mv` must rewrite every descendant key. The tree pays a little
  on `read` (walk the path) to make `ls` and subtree-`mv` structural and cheap.

## Run

There are no tests here — writing them is part of the exercise. Add a `test_filesystem.py` in this
directory (cover `mkdir -p` + sorted `ls`, a write/read round-trip, both cycle-free failure modes —
missing path and missing parent — `find` across the tree, and moving a directory subtree), then:

```
cd python-katas && .venv/bin/pytest practice/filesystem
```
Compare against the reference: `.venv/bin/pytest solution/filesystem`.

## Reference

Worked solution: `solution/filesystem/`.

Extension: add `rm`/`rmdir` (and decide whether `rmdir` requires the directory be empty); symbolic
links (resolution now has to follow — and guard against — link loops); or a `walk()` generator that
yields `(dirpath, dirnames, filenames)` like `os.walk`.

Background: [Python data model — emulating container types](https://docs.python.org/3/reference/datamodel.html#emulating-container-types).
