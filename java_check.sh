#\!/bin/bash
echo "Checking for Java installation methods..."

# Check for android-studio bundled java
if [ -d "/opt/android-studio" ]; then
    echo "Android Studio found, checking for bundled JDK..."
    find /opt/android-studio -name "java" -type f 2>/dev/null | head -3
fi

# Check for snap openjdk
if command -v snap > /dev/null; then
    echo "Checking snap packages..."
    snap list | grep -i openjdk || echo "No OpenJDK snap packages found"
fi

# Check common locations
echo "Checking common Java locations..."
for location in /usr/lib/jvm /opt/java /usr/java /Library/Java; do
    if [ -d "$location" ]; then
        echo "Found directory: $location"
        ls -la "$location" 2>/dev/null | head -3
    fi
done

# Check if we can install without sudo
echo "Checking package manager access..."
which apt-get > /dev/null && echo "apt-get available" || echo "apt-get not available"

echo "Environment check complete."
