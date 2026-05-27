#!/usr/bin/env bash

git config core.hooksPath .githooks

chmod +x .githooks/*

# to install the required tools for pre-commit formatting and linting, we can use the following commands:
# sudo apt update && \
# sudo apt install -y maven python3 python3-pip pipx && \
# pipx ensurepath && \
# pipx install black && \
# pipx install isort && \
# pipx install flake8