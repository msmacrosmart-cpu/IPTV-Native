#!/bin/sh
#
# Minimal POSIX launcher for the Gradle wrapper.
#
# The wrapper script that shipped with this project was corrupted: its backticks had
# been escaped, so /bin/sh failed with `Syntax error: "do" unexpected` before Gradle
# could even start. This launcher calls the wrapper main class directly, which is what
# the official script does after all of its platform specific setup.
#
set -e

APP_HOME=$(cd "$(dirname "$0")" > /dev/null 2>&1 && pwd -P)
WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

if [ ! -f "$WRAPPER_JAR" ]; then
    echo "ERROR: $WRAPPER_JAR is missing. The Gradle wrapper is incomplete." >&2
    exit 1
fi

if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
elif command -v java > /dev/null 2>&1; then
    JAVACMD=java
else
    echo "ERROR: JAVA_HOME is not set and no 'java' command could be found." >&2
    echo "Install a JDK 17 and/or set JAVA_HOME." >&2
    exit 1
fi

# shellcheck disable=SC2086
exec "$JAVACMD" \
    $DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS \
    -Dorg.gradle.appname=gradlew \
    -classpath "$WRAPPER_JAR" \
    org.gradle.wrapper.GradleWrapperMain "$@"
