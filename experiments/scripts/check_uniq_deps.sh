#!/bin/bash
# cut -d',' -f1 ../output/output.csv | tail -n +2 | sort | uniq > test.txt

# Check if the input file is provided as an argument
if [ "$#" -ne 1 ]; then
  echo "Usage: $0 <name_of_csv_to_check>"
  exit 1
fi

# Assign the argument to a variable
OUTPUT_CSV="$1"

# Process the file
cut -d',' -f1 ../output/"$OUTPUT_CSV" | tail -n +2 | sort | uniq > test/"$OUTPUT_CSV"_test.txt