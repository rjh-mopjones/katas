"""In-memory file system — path-based operations over a directory tree.

# The scenario

An in-memory file system models directories and files the way a real one does: directories nest to
form a tree, files hold content, and every operation names a node by an absolute ``/``-separated
path (``"/a/b/c"``, with ``"/"`` the root). ``mkdir`` creates a directory and every missing parent
(``mkdir -p``); ``write`` stores a file under an existing directory; ``read`` fetches its content;
``ls`` lists a directory's children; ``find`` locates every node with a given name; ``mv`` relocates
a file or a whole subtree.

# The design: a tree of nodes + recursive path resolution

The natural model is a tree. A [`Dir`] holds a ``dict[str, Node]`` mapping each child's *name* to
its node; a [`File`] holds a string of content. Every operation is "walk the path, then act": split
the path into components, descend the tree one component at a time from the root, and either resolve
to a node or fail. Resolution is where all the behaviour lives — a missing intermediate directory,
or an intermediate component that turns out to be a file rather than a directory, is what makes each
operation raise.

Two operations recurse over the tree rather than down a single path. ``find`` is a depth-first walk
of the whole tree collecting nodes whose basename matches. ``mv`` of a directory carries its entire
subtree along for free: because a ``Dir`` *is* the root of its subtree, re-parenting the one node
moves everything beneath it — no per-child copying.

``__contains__`` lets ``"/a/b" in fs`` read like membership, which is what path existence is.

An **alternative** model backs the file system with a flat ``dict[str, str]`` keyed by full path.
That makes ``read``/``write`` trivial (one dict lookup) but pushes the cost onto ``ls`` (scan every
key for those sharing a prefix) and ``mv`` of a directory (rewrite the key of every descendant). The
tree pays a little on ``read`` (walk the path) to make ``ls`` and subtree-``mv`` structural and
cheap — the right default when directory operations matter. (``pathlib``/``functools`` are the
stdlib tools you'd reach for around this, but the resolution here is done by hand so the tree walk
stays explicit.)
"""

from __future__ import annotations

from dataclasses import dataclass, field


class FileSystemError(Exception):
    """Raised when an operation is invalid: a missing path, a missing parent, or a type clash."""


@dataclass
class File:
    """A leaf node holding string content."""

    content: str


@dataclass
class Dir:
    """An interior node mapping child *names* to their nodes."""

    children: dict[str, Node] = field(default_factory=dict)


Node = File | Dir


def _split(path: str) -> list[str]:
    """Normalise an absolute path to its parts (``"/a/b"`` → ``["a", "b"]``; ``"/"`` → ``[]``)."""
    if not path.startswith("/"):
        raise FileSystemError(f"path must be absolute: {path!r}")
    return [part for part in path.split("/") if part]


class FileSystem:
    """A directory tree with path-addressed operations. You design the internals."""

    def __init__(self) -> None:
        self._root = Dir()

    def mkdir(self, path: str) -> None:
        """Create the directory at ``path`` and any missing parents (``mkdir -p``).

        Making an existing directory is a no-op; a component that is an existing file raises.
        """
        node: Dir = self._root
        for part in _split(path):
            child = node.children.get(part)
            if child is None:
                child = Dir()
                node.children[part] = child
            elif isinstance(child, File):
                raise FileSystemError(f"not a directory: {part!r} in {path!r}")
            node = child

    def write(self, path: str, content: str) -> None:
        """Create or overwrite the file at ``path``; its parent directory must already exist."""
        parts = _split(path)
        if not parts:
            raise FileSystemError("cannot write to the root")
        parent = self._resolve_dir(parts[:-1], path)
        name = parts[-1]
        existing = parent.children.get(name)
        if isinstance(existing, Dir):
            raise FileSystemError(f"is a directory: {path!r}")
        parent.children[name] = File(content)

    def read(self, path: str) -> str:
        """Return the content of the file at ``path``; a missing path or a directory raises."""
        node = self._resolve(_split(path), path)
        if isinstance(node, Dir):
            raise FileSystemError(f"is a directory: {path!r}")
        return node.content

    def ls(self, path: str) -> list[str]:
        """List the sorted child names of a directory; for a file, its basename in a list."""
        parts = _split(path)
        node = self._resolve(parts, path)
        if isinstance(node, File):
            return [parts[-1]] if parts else [""]
        return sorted(node.children)

    def find(self, name: str) -> list[str]:
        """Return the sorted absolute paths of every node whose basename equals ``name``."""
        found: list[str] = []

        def walk(node: Dir, prefix: str) -> None:
            for child_name, child in node.children.items():
                path = f"{prefix}/{child_name}"
                if child_name == name:
                    found.append(path)
                if isinstance(child, Dir):
                    walk(child, path)

        walk(self._root, "")
        return sorted(found)

    def mv(self, src: str, dst: str) -> None:
        """Move the node at ``src`` (file or subtree) to ``dst``; ``dst``'s parent must exist."""
        src_parts = _split(src)
        dst_parts = _split(dst)
        if not src_parts:
            raise FileSystemError("cannot move the root")
        if not dst_parts:
            raise FileSystemError("cannot move onto the root")
        src_parent = self._resolve_dir(src_parts[:-1], src)
        node = src_parent.children.get(src_parts[-1])
        if node is None:
            raise FileSystemError(f"no such path: {src!r}")
        dst_parent = self._resolve_dir(dst_parts[:-1], dst)
        del src_parent.children[src_parts[-1]]
        dst_parent.children[dst_parts[-1]] = node

    def __contains__(self, path: str) -> bool:
        try:
            self._resolve(_split(path), path)
        except FileSystemError:
            return False
        return True

    def _resolve(self, parts: list[str], path: str) -> Node:
        node: Node = self._root
        for part in parts:
            if isinstance(node, File):
                raise FileSystemError(f"not a directory: {path!r}")
            child = node.children.get(part)
            if child is None:
                raise FileSystemError(f"no such path: {path!r}")
            node = child
        return node

    def _resolve_dir(self, parts: list[str], path: str) -> Dir:
        node = self._resolve(parts, path)
        if isinstance(node, File):
            raise FileSystemError(f"not a directory: {path!r}")
        return node
