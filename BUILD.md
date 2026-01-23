# MCraze Build Guide

This guide explains how to build and package MCraze for distribution.

## Prerequisites

- **Java Development Kit (JDK) 21** or higher
  - Download from: https://www.oracle.com/java/technologies/downloads/
  - Or use OpenJDK: https://adoptium.net/
- **Windows**: No additional tools needed
- **Linux/Mac**: Make sure `bash` and `chmod` are available

## Quick Start

### Windows

```cmd
REM Option 1: Build standalone JAR (recommended for distribution)
build-uber-jar.bat

REM Option 2: Build regular JAR (requires lib/ folder)
build-jar.bat

REM Option 3: Build native Windows installer (.msi)
build-uber-jar.bat
build-installer.bat
```

### Linux/Mac

```bash
# Make scripts executable (first time only)
chmod +x build-uber-jar.sh

# Build standalone JAR
./build-uber-jar.sh
```

## Build Options

### 1. Uber-JAR (Standalone) - **RECOMMENDED**

Creates a single JAR file with all dependencies included.

**File**: `build-uber-jar.bat` (Windows) or `build-uber-jar.sh` (Linux/Mac)

**Output**: `MCraze-standalone.jar` (~250 KB)

**Advantages**:
- Single file distribution
- No external dependencies needed
- Easy to run: `java -jar MCraze-standalone.jar`

**Run with**:
```bash
java -jar MCraze-standalone.jar
```

**Dedicated server**:
```bash
java -jar MCraze-standalone.jar --dedicated-server --port 25565
```

---

### 2. Regular JAR

Creates a JAR that requires the `lib/` folder to be present.

**File**: `build-jar.bat`

**Output**: `MCraze.jar`

**Advantages**:
- Smaller JAR file
- Dependencies can be updated separately

**Disadvantages**:
- Must distribute with `lib/` folder
- Users need to maintain correct folder structure

**Run with**:
```bash
java -jar MCraze.jar
```

---

### 3. Native Installer (Windows Only)

Creates a native Windows installer (.msi) with bundled JRE.

**File**: `build-installer.bat`

**Prerequisites**: Must run `build-uber-jar.bat` first

**Output**: `installer/MCraze-1.0.0.msi` (or portable `installer/MCraze/` folder)

**Advantages**:
- Professional installer experience
- Bundles Java runtime (users don't need Java installed)
- Creates Start Menu shortcuts
- Includes uninstaller
- ~100-150 MB (includes JRE)

**Disadvantages**:
- Large file size
- Windows only
- Longer build time (2-5 minutes)

**Run with**:
- Install the .msi file
- Run from Start Menu or Desktop shortcut

---

## Build Process Details

### What gets included:

1. **Compiled classes** from `src/`
2. **Resources**:
   - `src/assets/` - Game textures, Music and sound effects
   - `src/items/` - Item definitions (items.json)
3. **Dependencies** (uber-JAR only):
   - gson-2.1.jar (JSON parsing)
   - easyogg.jar, jogg-0.0.7.jar, jorbis-0.0.15.jar (OGG music playback)

### Build artifacts:

```
MCraze/
├── MCraze-standalone.jar      # Uber-JAR (all-in-one)
├── MCraze.jar                 # Regular JAR (needs lib/)
├── lib/                       # Dependencies (for regular JAR)
├── build/                     # Temporary build files (auto-cleaned)
└── installer/                 # Native installer output
    └── MCraze-1.0.0.msi      # Windows installer
```

## Distribution

### For End Users (Recommended):

1. **Windows**: Distribute `installer/MCraze-1.0.0.msi`
   - Users just double-click to install
   - No Java installation required

2. **All Platforms**: Distribute `MCraze-standalone.jar`
   - Users need Java 21+ installed
   - Run with: `java -jar MCraze-standalone.jar`

### For Developers:

Distribute the entire repository with build scripts included.

## Troubleshooting

### "javac is not recognized"

**Problem**: Java is not in PATH

**Solution**:
- Windows: Set `JAVA_HOME` environment variable to JDK installation path
- Or edit build scripts to use full path to javac

### Build fails with "compilation error"

**Problem**: Source code has errors

**Solution**:
- Check console output for specific error messages
- Ensure all dependencies are in `lib/` folder
- Verify Java version is 21+

### JPackage fails with "wixl not found" or similar

**Problem**: WiX Toolset not installed (needed for MSI on Windows)

**Solution**:
- Install WiX Toolset: https://wixtoolset.org/
- Or use the app-image fallback (creates portable .exe instead)

### JAR runs but shows errors

**Problem**: Resources not included

**Solution**:
- Ensure `sprites/`, `sounds/`, and `items/` folders exist in build
- Check that resources are being copied correctly
- For uber-JAR, verify dependencies were extracted

## Advanced Options

### Custom JVM Options

Edit the build scripts to add JVM flags:

```batch
--java-options "-Xmx4G"              REM Increase max memory to 4GB
--java-options "-XX:+UseG1GC"        REM Use G1 garbage collector
--java-options "-Dfile.encoding=UTF-8"  REM Force UTF-8 encoding
```

### Custom Icon

Add to `build-installer.bat`:

```batch
--icon path/to/icon.ico
```

### Version Number

Edit `build-installer.bat`:

```batch
set VERSION=2.0.0
```

## Continuous Integration

To automate builds with GitHub Actions, create `.github/workflows/build.yml`:

```yaml
name: Build MCraze
on: [push, pull_request]

jobs:
  build:
    runs-on: windows-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '21'
          distribution: 'temurin'
      - name: Build Uber-JAR
        run: build-uber-jar.bat
      - name: Upload Artifact
        uses: actions/upload-artifact@v3
        with:
          name: MCraze-standalone
          path: MCraze-standalone.jar
```

## License

All build scripts are licensed under GPL-3.0, same as MCraze.

## Support

For build issues, check:
1. Java version: `java -version` (must be 21+)
2. Dependencies: All JARs in `lib/` folder
3. Console output for specific errors
