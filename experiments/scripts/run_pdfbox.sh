#!/bin/bash

# Get the directory of the script
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Define paths relative to the script's directory
CLASS_PORT_AGENT="$SCRIPT_DIR/../../classport-instr-agent/target/classport-instr-agent-0.1.0-SNAPSHOT.jar"
PDFBOX_APP="$SCRIPT_DIR/../pdfbox-3.0.4/app/target/pdfbox-app-3.0.4.jar"
INPUT_PDF="$SCRIPT_DIR/../test.pdf"
PROJECT_NAME="pdfbox"
OUTPUT_DIR="../output"

# Check if required files exist
if [[ ! -f "$CLASS_PORT_AGENT" ]]; then
  echo "Error: CLASS_PORT_AGENT not found at $CLASS_PORT_AGENT"
  exit 1
fi

if [[ ! -f "$PDFBOX_APP" ]]; then
  echo "Error: PDFBOX_APP not found at $PDFBOX_APP"
  exit 1
fi

if [[ ! -f "$INPUT_PDF" ]]; then
  echo "Error: INPUT_PDF not found at $INPUT_PDF"
  exit 1
fi

# Run the command
java -javaagent:"$CLASS_PORT_AGENT"="$PROJECT_NAME","$OUTPUT_DIR" -jar "$PDFBOX_APP" export:text -i="$INPUT_PDF"