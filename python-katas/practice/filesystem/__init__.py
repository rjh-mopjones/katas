"""In-memory file system — implement `FileSystem`; `FileSystemError` is provided."""

from __future__ import annotations


class FileSystemError(Exception):
    """Raised when an operation is invalid: a missing path, a missing parent, or a type clash."""


class FileSystem:
    """A directory tree with path-addressed operations. You design the internals."""

    def __init__(self) -> None:
        raise NotImplementedError

    def mkdir(self, path: str) -> None:
        raise NotImplementedError

    def write(self, path: str, content: str) -> None:
        raise NotImplementedError

    def read(self, path: str) -> str:
        raise NotImplementedError

    def ls(self, path: str) -> list[str]:
        raise NotImplementedError

    def find(self, name: str) -> list[str]:
        raise NotImplementedError

    def mv(self, src: str, dst: str) -> None:
        raise NotImplementedError

    def __contains__(self, path: str) -> bool:
        raise NotImplementedError
