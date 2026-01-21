#!/bin/sh
APP_HOME="$(dirname "$0")"
GRADLE_WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
exec java -jar "$GRADLE_WRAPPER_JAR" "$@"
