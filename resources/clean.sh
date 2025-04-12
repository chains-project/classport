#!/bin/bash

# Get the directory of the script
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Function to remove all "classport-files" folders
clean_classport_files() {
    echo "Removing all 'classport-files' folders..."
    find "$SCRIPT_DIR/" -type d -name "classport-files" -exec rm -rf {} +
    echo "'classport-files' folders removed."
}

# Function to remove the content of the "output" folder
clean_output_folder() {
    echo "Cleaning the 'output' folder..."
    if [ -d "$SCRIPT_DIR/output" ]; then
        rm -rf "$SCRIPT_DIR/output"/*
        echo "'output' folder cleaned."
    else
        echo "'output' folder does not exist."
    fi
}

# Function to remove the content of the "test" folder
clean_test_folder() {
    echo "Cleaning the 'test-on-output' folder..."
    if [ -d "$SCRIPT_DIR/test-on-output" ]; then
        rm -rf "$SCRIPT_DIR/test-on-output"/*
        echo "'test-on-output' folder cleaned."
    else
        echo "'test-on-output' folder does not exist."
    fi
}

# Display usage information
usage() {
    echo "Usage: $0 [options]"
    echo "Options:"
    echo "  --classport-files   Clean all 'classport-files' folders"
    echo "  --output            Clean the 'output' folder"
    echo "  --test              Clean the 'test' folder"
    echo "  --all               Clean everything"
    echo "  --help              Display this help message"
}

# Parse command-line arguments
if [ $# -eq 0 ]; then
    usage
    exit 1
fi

for arg in "$@"; do
    case $arg in
        --classport-files)
            clean_classport_files
            ;;
        --output)
            clean_output_folder
            ;;
        --test)
            clean_test_folder
            ;;
        --all)
            clean_classport_files
            clean_output_folder
            clean_test_folder
            ;;
        --help)
            usage
            exit 0
            ;;
        *)
            echo "Unknown option: $arg"
            usage
            exit 1
            ;;
    esac
done