#!/bin/bash

# Get the directory of the script
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Define paths relative to the script's directory
CLASS_PORT_AGENT="$SCRIPT_DIR/../classport-instr-agent/target/classport-instr-agent-0.1.0-SNAPSHOT.jar"
SIMPLE_APP="$SCRIPT_DIR/multi/executor/target/executor-1.0.jar"
PROJECT_NAME="executor"
OUTPUT_DIR="output"

# Check if required files exist
if [[ ! -f "$CLASS_PORT_AGENT" ]]; then
  echo "Error: CLASS_PORT_AGENT not found at $CLASS_PORT_AGENT"
  exit 1
fi

if [[ ! -f "$SIMPLE_APP" ]]; then
  echo "Error: SIMPLE_APP not found at $SIMPLE_APP"
  exit 1
fi
# Create the output directory if it doesn't exist
mkdir -p "$OUTPUT_DIR"

# Run the command
java -javaagent:"$CLASS_PORT_AGENT"="$PROJECT_NAME","$OUTPUT_DIR" -jar "$SIMPLE_APP" 