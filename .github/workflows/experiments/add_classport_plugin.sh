#!/bin/bash

# Script to add classport-maven-plugin to pom.xml
# Usage: ./add_classport_plugin.sh [pom_file_path]
# If pom_file_path is not provided, defaults to "pom.xml" in current directory

POM_FILE="${1:-pom.xml}"

# Check if xmlstarlet is available
if ! command -v xmlstarlet &> /dev/null; then
    echo "Error: xmlstarlet is not installed. Please install it first."
    exit 1
fi

# Check if pom.xml exists
if [ ! -f "$POM_FILE" ]; then
    echo "Error: pom.xml not found at $POM_FILE"
    exit 1
fi

# Determine if the document uses namespaces
HAS_NAMESPACE=false
if xmlstarlet sel -N x=http://maven.apache.org/POM/4.0.0 -t -c "/x:project" "$POM_FILE" &>/dev/null; then
    HAS_NAMESPACE=true
fi

# Check if build/plugins already exists, if not create it
# Try with namespace first, then without (only match /project/build/plugins, not pluginManagement)
if [ "$HAS_NAMESPACE" = true ]; then
    BUILD_PLUGINS_EXISTS=$(xmlstarlet sel -N x=http://maven.apache.org/POM/4.0.0 -t -c "/x:project/x:build/x:plugins" "$POM_FILE" 2>/dev/null)
else
    BUILD_PLUGINS_EXISTS=$(xmlstarlet sel -t -c "/project/build/plugins" "$POM_FILE" 2>/dev/null)
fi

if [ -z "$BUILD_PLUGINS_EXISTS" ]; then
    # Check if build exists, if not create it
    if [ "$HAS_NAMESPACE" = true ]; then
        BUILD_EXISTS=$(xmlstarlet sel -N x=http://maven.apache.org/POM/4.0.0 -t -c "/x:project/x:build" "$POM_FILE" 2>/dev/null)
    else
        BUILD_EXISTS=$(xmlstarlet sel -t -c "/project/build" "$POM_FILE" 2>/dev/null)
    fi
    
    if [ -z "$BUILD_EXISTS" ]; then
        # Create build element
        if [ "$HAS_NAMESPACE" = true ]; then
            xmlstarlet ed -L -N x=http://maven.apache.org/POM/4.0.0 -s "/x:project" -t elem -n "build" "$POM_FILE"
        else
            xmlstarlet ed -L -s "/project" -t elem -n "build" "$POM_FILE"
        fi
    fi
    # Create plugins element (only if it doesn't exist directly under build)
    if [ "$HAS_NAMESPACE" = true ]; then
        if ! xmlstarlet sel -N x=http://maven.apache.org/POM/4.0.0 -t -c "/x:project/x:build/x:plugins" "$POM_FILE" 2>/dev/null; then
            xmlstarlet ed -L -N x=http://maven.apache.org/POM/4.0.0 -s "/x:project/x:build" -t elem -n "plugins" "$POM_FILE"
        fi
    else
        if ! xmlstarlet sel -t -c "/project/build/plugins" "$POM_FILE" 2>/dev/null; then
            xmlstarlet ed -L -s "/project/build" -t elem -n "plugins" "$POM_FILE"
        fi
    fi
fi

# Check if the plugin already exists (to avoid duplicates)
# Try with namespace first, then without
PLUGIN_EXISTS=$(xmlstarlet sel -N x=http://maven.apache.org/POM/4.0.0 -t -v "count(//x:plugin[x:groupId='io.github.project' and x:artifactId='classport-maven-plugin'])" "$POM_FILE" 2>/dev/null)
if [ -z "$PLUGIN_EXISTS" ] || [ "$PLUGIN_EXISTS" = "" ]; then
    PLUGIN_EXISTS=$(xmlstarlet sel -t -v "count(//plugin[groupId='io.github.project' and artifactId='classport-maven-plugin'])" "$POM_FILE" 2>/dev/null || echo "0")
fi

if [ "$PLUGIN_EXISTS" -eq 0 ]; then
    # Use the same namespace context as determined earlier
    if [ "$HAS_NAMESPACE" = true ]; then
        # Use namespace-aware XPath for editing - only match /project/build/plugins (not pluginManagement)
        xmlstarlet ed -L -N x=http://maven.apache.org/POM/4.0.0 -s "/x:project/x:build/x:plugins" -t elem -n "plugin" -v "" "$POM_FILE"
        xmlstarlet ed -L -N x=http://maven.apache.org/POM/4.0.0 -s "/x:project/x:build/x:plugins/x:plugin[last()]" -t elem -n "groupId" -v "io.github.project" "$POM_FILE"
        xmlstarlet ed -L -N x=http://maven.apache.org/POM/4.0.0 -s "/x:project/x:build/x:plugins/x:plugin[last()]" -t elem -n "artifactId" -v "classport-maven-plugin" "$POM_FILE"
        xmlstarlet ed -L -N x=http://maven.apache.org/POM/4.0.0 -s "/x:project/x:build/x:plugins/x:plugin[last()]" -t elem -n "version" -v "0.1.0-SNAPSHOT" "$POM_FILE"
        xmlstarlet ed -L -N x=http://maven.apache.org/POM/4.0.0 -s "/x:project/x:build/x:plugins/x:plugin[last()]" -t elem -n "executions" -v "" "$POM_FILE"
        xmlstarlet ed -L -N x=http://maven.apache.org/POM/4.0.0 -s "/x:project/x:build/x:plugins/x:plugin[last()]/x:executions" -t elem -n "execution" -v "" "$POM_FILE"
        xmlstarlet ed -L -N x=http://maven.apache.org/POM/4.0.0 -s "/x:project/x:build/x:plugins/x:plugin[last()]/x:executions/x:execution" -t elem -n "goals" -v "" "$POM_FILE"
        xmlstarlet ed -L -N x=http://maven.apache.org/POM/4.0.0 -s "/x:project/x:build/x:plugins/x:plugin[last()]/x:executions/x:execution/x:goals" -t elem -n "goal" -v "embed" "$POM_FILE"
    else
        # Use non-namespace XPath
        xmlstarlet ed -L -s "/project/build/plugins" -t elem -n "plugin" -v "" "$POM_FILE"
        xmlstarlet ed -L -s "/project/build/plugins/plugin[last()]" -t elem -n "groupId" -v "io.github.project" "$POM_FILE"
        xmlstarlet ed -L -s "/project/build/plugins/plugin[last()]" -t elem -n "artifactId" -v "classport-maven-plugin" "$POM_FILE"
        xmlstarlet ed -L -s "/project/build/plugins/plugin[last()]" -t elem -n "version" -v "0.1.0-SNAPSHOT" "$POM_FILE"
        xmlstarlet ed -L -s "/project/build/plugins/plugin[last()]" -t elem -n "executions" -v "" "$POM_FILE"
        xmlstarlet ed -L -s "/project/build/plugins/plugin[last()]/executions" -t elem -n "execution" -v "" "$POM_FILE"
        xmlstarlet ed -L -s "/project/build/plugins/plugin[last()]/executions/execution" -t elem -n "goals" -v "" "$POM_FILE"
        xmlstarlet ed -L -s "/project/build/plugins/plugin[last()]/executions/execution/goals" -t elem -n "goal" -v "embed" "$POM_FILE"
    fi
    echo "Plugin configuration added to pom.xml"
else
    echo "Plugin already exists in pom.xml, skipping addition"
fi

