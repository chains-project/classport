#!/bin/bash

# Ensure required argument is provided
if [ $# -lt 1 ]; then
  echo "Usage: $0 <program_name>"
  echo "Supported programs: simple multi"
  exit 1
fi

# Program name passed as an argument
PROGRAM=$1

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_NAME=$PROGRAM

case $PROGRAM in
  simple)
    # Set the path for the merged local Maven repository
    MERGED_REPO="simple-app-monitoring/all-classport-files"
    PROJECT_FOLDER="simple-app-monitoring"
  ;;
  multi)
    # Set the path for the merged local Maven repository
    MERGED_REPO="multi/all-classport-files"
    PROJECT_FOLDER="multi"
  ;;
  *)
    echo "Unknown program: $PROGRAM"
    exit 1
  ;;
esac

# Clean up any existing repo directory (optional)
rm -rf "$MERGED_REPO"
mkdir -p "$MERGED_REPO"

# Find and copy all classport-files folders from submodules
find ${PROJECT_FOLDER} -type d -name "classport-files" | while read -r EXP_DIR; do
  echo "Copying from $EXP_DIR"
  cp -r "$EXP_DIR/"* "$MERGED_REPO/"
done

echo "✅ All classport-files merged into $MERGED_REPO"
