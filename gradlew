#!/bin/sh

APP_HOME="$(cd "$(dirname "$0")/.." && pwd)"

JAVACMD="/Library/Java/JavaVirtualMachines/temurin-26.jdk/Contents/Home/bin/java"

if [ ! -x "$JAVACMD" ] ; then
    echo "ERROR: JAVA_HOME is not set correctly" >&2
    exit 1
fi

GRADLE_OPTS="-Xmx2048m"

exec "$JAVACMD" \
    $GRADLE_OPTS \
    "-Dorg.gradle.appname=$0" \
    "-Dorg.gradle.jvmargs=-Xmx2048m" \
    "-classpath" "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" \
    "org.gradle.wrapper.GradleWrapperMain" \
    "$@"
