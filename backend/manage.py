import argparse
from .config import Settings
from .contracts import RULE_SCHEMA, SCHEMA_VERSION
import json


def main():
    parser = argparse.ArgumentParser(description="本地开发环境管理；令牌仅在终端显式显示")
    parser.add_argument("command", choices=["token", "schema"])
    parser.add_argument("role", nargs="?", choices=["admin", "reviewer", "developer", "agent"], default="admin")
    args = parser.parse_args()
    if args.command == "token":
        print(Settings().tokens[args.role])
    else:
        print(json.dumps({"version": SCHEMA_VERSION, "extraction": RULE_SCHEMA}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
