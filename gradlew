#!/bin/bash

# Gradle wrapper bootstrap script
# This downloads the Gradle wrapper JAR and runs it

GRADLE_VERSION="8.9"
WRAPPER_JAR="gradle/wrapper/gradle-wrapper.jar"
WRAPPER_URL="https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"

if [ ! -f "$WRAPPER_JAR" ]; then
    echo "Downloading Gradle wrapper..."
    mkdir -p gradle/wrapper
    # Download the gradle wrapper jar from the Gradle distributions
    curl -sL "https://raw.githubusercontent.com/gradle/gradle/v${GRADLE_VERSION}/gradle/wrapper/gradle-wrapper.jar" -o "$WRAPPER_JAR"
fi

java -jar "$WRAPPER_JAR" "$@"
