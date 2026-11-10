#!/bin/sh

# Gradle wrapper script for KALI MOBILE
# Generated for Android Studio compatibility

APP_HOME=$(cd "$(dirname "$0")" && pwd)

# Determine the Java command to use
if [ -n "$JAVA_HOME" ] ; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD=java
fi

if [ ! -x "$JAVACMD" ] ; then
    echo "ERROR: JAVA_HOME is not set correctly or java is not found."
    echo "Please set JAVA_HOME to your JDK installation."
    exit 1
fi

# Set the Gradle distribution URL
GRADLE_WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

if [ ! -f "$GRADLE_WRAPPER_JAR" ]; then
    echo "Downloading Gradle wrapper..."
    # The wrapper jar will be downloaded when you run 'gradle wrapper' on a machine with Java
fi

# Add the app directory to the classpath
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

# Execute Gradle
exec "$JAVACMD" -cp "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"