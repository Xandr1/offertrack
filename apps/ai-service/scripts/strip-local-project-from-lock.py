import argparse
import tomllib
from pathlib import Path

LOCAL_PROJECT_NAME = "offertrack-ai-service"
LOCAL_PROJECT_STANZA = f'''[[packages]]
name = "{LOCAL_PROJECT_NAME}"

[packages.directory]
path = "."

'''


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Remove pip's local project entry from a generated dependency lock."
    )
    parser.add_argument("lock_path", type=Path)
    return parser.parse_args()


def main() -> None:
    lock_path = parse_args().lock_path
    lock_text = lock_path.read_text(encoding="utf-8")
    lock = tomllib.loads(lock_text)
    local_projects = [
        package for package in lock.get("packages", []) if package.get("name") == LOCAL_PROJECT_NAME
    ]
    expected_project = {"name": LOCAL_PROJECT_NAME, "directory": {"path": "."}}

    if local_projects != [expected_project] or lock_text.count(LOCAL_PROJECT_STANZA) != 1:
        raise SystemExit(
            "Expected exactly one pip 26.1.2 local offertrack-ai-service directory stanza."
        )

    dependency_lock = lock_text.replace(LOCAL_PROJECT_STANZA, "")
    if any(
        package.get("name") == LOCAL_PROJECT_NAME
        for package in tomllib.loads(dependency_lock).get("packages", [])
    ):
        raise SystemExit("Failed to remove the local offertrack-ai-service directory stanza.")

    lock_path.write_text(dependency_lock, encoding="utf-8", newline="\n")


if __name__ == "__main__":
    main()
