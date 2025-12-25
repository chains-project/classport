#!/bin/bash

# Usage: ./compute_build_overhead.sh <program_name>
# Ensure required argument is provided
if [ $# -lt 1 ]; then
  echo "Usage: $0 <program_name> "
  echo "Supported programs: pdfbox, mcs, ripper, batik, checkstyle, zxing"
  exit 1
fi

# Program name passed as an argument
PROGRAM=$1

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

case $PROGRAM in
  pdfbox)
    PROJECT_DIR="$SCRIPT_DIR/../projects/pdfbox-3.0.4"
    ;;
  ripper)
    PROJECT_DIR="$SCRIPT_DIR/../projects/certificate-ripper-2.4.1"
    ;;
  mcs)
    PROJECT_DIR="$SCRIPT_DIR/../projects/mcs-0.7.3"
    ;;
  batik)
    PROJECT_DIR="$SCRIPT_DIR/../projects/batikwrapper"
    ;;
  checkstyle)
    PROJECT_DIR="$SCRIPT_DIR/../projects/checkstyle-checkstyle-10.23.0"
    ;;
  zxing)
    PROJECT_DIR="$SCRIPT_DIR/../projects/zxing-wrapper"
    ;;
#   jacop)
#     PROJECT_DIR="$SCRIPT_DIR/../projects/jacop-4.10.0/target/jacop-4.10.0.jar"
#     ;;

#   ttorrent)
#     PROJECT_DIR="$SCRIPT_DIR/../projects/ttorrent-ttorrent-1.5/cli/target/ttorrent-cli-1.5-shaded.jar"
#     ;;
#   graph)
#     PROJECT_DIR="$SCRIPT_DIR/../projects/graphhopper/graphhopper/web/target/graphhopper-web-9.1.jar"
#     ;;
#   commons)
#     PROJECT_DIR="$SCRIPT_DIR/../projects/commons-validator-1.9.0-src/target/commons-validator-1.9.0.jar"
#     ;;
  *)
    echo "Error: Unsupported program '$PROGRAM'"
    echo "Supported programs: pdfbox, checkstyle"
    exit 1
    ;;
esac

case $PROGRAM in
  pdfbox)
    POM_FILE="app/pom.xml"
    ;;
  *)
    POM_FILE="pom.xml"
    ;;
esac

cd "$PROJECT_DIR" || exit 1
# Measure baseline build time
echo "Measuring baseline build time..."
BASELINE_TIME=$( { time mvn clean package -DskipTests; } 2>&1 | grep real | awk '{print $2}' )

cp $PROJECT_DIR/$POM_FILE $PROJECT_DIR/$POM_FILE.bak

echo "Adding classport-maven-plugin to pom.xml..."
"$SCRIPT_DIR/add_classport_plugin.sh" $PROJECT_DIR/$POM_FILE

# Measure plugin execution time
echo "Measuring plugin execution time..."
PLUGIN_TIME=$( { time mvn clean package -DskipTests; } 2>&1 | grep real | awk '{print $2}' )

mv $PROJECT_DIR/$POM_FILE.bak $PROJECT_DIR/$POM_FILE


# Convert times to seconds
BASELINE_SECONDS=$(echo $BASELINE_TIME | awk -Fm '{print $1 * 60 + $2}')
PLUGIN_SECONDS=$(echo $PLUGIN_TIME | awk -Fm '{print $1 * 60 + $2}')

# Compute overhead
OVERHEAD=$(echo "$PLUGIN_SECONDS - $BASELINE_SECONDS" | bc)
PERCENTAGE_OVERHEAD=$(echo "scale=2; ($OVERHEAD / $BASELINE_SECONDS) * 100" | bc)

echo "Baseline build time: $BASELINE_TIME"
echo "Plugin execution time: $PLUGIN_TIME"
echo "Overhead introduced by the plugin: ${OVERHEAD}s"
echo "Percentage overhead: ${PERCENTAGE_OVERHEAD}%"

