#!/bin/sh
#
# Copyright © 2015-2021 the original authors.
# Gradle Wrapper Shell Script
#
APP_HOME="$(cd "$(dirname "$0")" && pwd)"
APP_NAME="Gradle"
APP_BASE_NAME="$(basename "$0")"
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'
JAVA_HOME_CANDIDATES=""

warn () { echo "$*"; }
die () { echo; echo "$*"; echo; exit 1; }

# Find java
if [ -n "$JAVA_HOME" ] ; then
    JAVA_HOME_CANDIDATES="$JAVA_HOME/bin/java"
fi
JAVA_EXE=""
for CANDIDATE in $JAVA_HOME_CANDIDATES java; do
    if command -v "$CANDIDATE" >/dev/null 2>&1; then
        JAVA_EXE="$CANDIDATE"
        break
    fi
done
[ -z "$JAVA_EXE" ] && die "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH."

CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
eval set -- "$DEFAULT_JVM_OPTS"
exec "$JAVA_EXE" "$@" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$APP_BASE_NAME" "$@"
