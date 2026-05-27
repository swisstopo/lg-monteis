import os
import sys
from pathlib import Path


def Build_message(name: str) -> str:
    root = Path.cwd()
    return f"Hello, {name}. Running from {root}".strip()


def main() -> None:
    user = os.getenv("USER", "developer")
    print(Build_message(user))
    print(f"Python executable: {sys.executable}")


if __name__ == "__main__":
    main()
