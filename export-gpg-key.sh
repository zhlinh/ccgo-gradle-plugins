#!/bin/bash
# Script to export GPG key for use with Gradle signing

# Detect the first available secret key
KEY_ID=$(gpg --list-secret-keys --keyid-format LONG | grep '^sec' | head -1 | awk '{print $2}' | cut -d'/' -f2)

if [ -z "$KEY_ID" ]; then
    echo "❌ No GPG secret keys found!"
    echo ""
    echo "Please create a GPG key first:"
    echo "  gpg --gen-key"
    echo ""
    echo "Then run this script again."
    exit 1
fi

echo "================================================"
echo "GPG Key Export for Maven Central Publishing"
echo "================================================"
echo ""
echo "Using GPG key: $KEY_ID"
echo ""
echo "⚠️  This will export your PRIVATE key."
echo "    Keep it secure and never commit to version control!"
echo ""

# Configure GPG to use the current terminal
export GPG_TTY=$(tty)
export PINENTRY_USER_DATA="USE_CURSES=1"

# Export the private key (will prompt for passphrase)
echo "You will be prompted to enter your GPG key passphrase..."
echo ""
gpg --batch --pinentry-mode loopback --export-secret-keys --armor $KEY_ID > /tmp/maven-signing-key.asc 2>&1

# If batch mode fails, try without batch mode (interactive)
if [ $? -ne 0 ] || [ ! -s /tmp/maven-signing-key.asc ]; then
    echo "Batch mode failed, trying interactive mode..."
    gpg --export-secret-keys --armor $KEY_ID > /tmp/maven-signing-key.asc
fi

if [ $? -ne 0 ] || [ ! -s /tmp/maven-signing-key.asc ]; then
    echo "❌ Failed to export key."
    echo ""
    echo "Please export manually:"
    echo "  gpg --export-secret-keys --armor $KEY_ID > /tmp/maven-signing-key.asc"
    echo ""
    echo "Then run this script again, or manually convert the key:"
    echo "  cat /tmp/maven-signing-key.asc | sed 's/\$/\\\\n/' | tr -d '\\n' | sed 's/\\\\n\$//' "
    exit 1
fi

# Check file size
FILE_SIZE=$(wc -c < /tmp/maven-signing-key.asc)
if [ $FILE_SIZE -lt 1000 ]; then
    echo "❌ Exported key is too small ($FILE_SIZE bytes). Something went wrong."
    rm /tmp/maven-signing-key.asc
    exit 1
fi

echo "✅ Key exported successfully ($FILE_SIZE bytes)"
echo ""
echo "================================================"
echo "Step 1: Convert Key to Gradle Properties Format"
echo "================================================"
echo ""

# Convert newlines to \n for gradle.properties
KEY_CONTENT=$(cat /tmp/maven-signing-key.asc | sed 's/$/\\n/' | tr -d '\n' | sed 's/\\n$//')

echo "Copy the following line and add it to ~/.gradle/gradle.properties:"
echo ""
echo "----------------------------------------"
echo "signingInMemoryKey=$KEY_CONTENT"
echo "signingInMemoryKeyPassword=YOUR_GPG_PASSPHRASE"
echo "----------------------------------------"
echo ""
echo "================================================"
echo "Step 2: Verify the Key Format"
echo "================================================"
echo ""
echo "The key should:"
echo "  ✓ Start with: -----BEGIN PGP PRIVATE KEY BLOCK-----"
echo "  ✓ End with: -----END PGP PRIVATE KEY BLOCK-----"
echo "  ✓ Be on ONE LONG LINE (no actual newlines)"
echo "  ✓ Have \\n instead of real newlines"
echo "  ✓ NOT have quotes around it"
echo "  ✓ Be ~$FILE_SIZE characters long"
echo ""
echo "================================================"
echo "Step 3: Test Signing"
echo "================================================"
echo ""
echo "After updating gradle.properties, test with:"
echo "  ./gradlew clean"
echo "  ./gradlew signPluginMavenPublication"
echo ""
echo "You should see: 'In-memory PGP key configured successfully'"
echo ""
echo "================================================"
echo "Step 4: Clean Up"
echo "================================================"
echo ""
echo "Delete the temporary key file:"
echo "  rm /tmp/maven-signing-key.asc"
echo ""
echo "Or run this command now:"
read -p "Delete /tmp/maven-signing-key.asc now? (y/N) " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    rm /tmp/maven-signing-key.asc
    echo "✅ Temporary file deleted."
else
    echo "⚠️  Remember to delete /tmp/maven-signing-key.asc when done!"
fi
echo ""
echo "================================================"
echo "All Done!"
echo "================================================"
