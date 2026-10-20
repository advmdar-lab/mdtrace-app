#!/bin/sh
#
# Gradle start up script for UN*X
#
DIRNAME="$(dirname "$0")"
APP_HOME="$DIRNAME"
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
exec java -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
