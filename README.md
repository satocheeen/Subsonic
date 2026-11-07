## Build the Android compatibility branch

Clone with the required ServerProxy submodule:

```sh
git clone --recurse-submodules --branch fix/android17-compatibility https://github.com/satocheeen/Subsonic.git
cd Subsonic
```

If you already cloned the repository, run this from its root (the directory
containing `settings.gradle`), including in Android Studio's Terminal:

```sh
git submodule update --init --recursive
```

Then sync the project with Gradle in Android Studio and rebuild. GitHub's ZIP
download does not contain the submodule sources; use a Git clone for building.

This branch uses Gradle 8.11.1 / Android Gradle Plugin 8.9.2. Set Android
Studio's **Gradle JDK** to JDK 17 or 21 (File > Settings > Build, Execution,
Deployment > Build Tools > Gradle). For terminal builds, set `JAVA_HOME` to the
same JDK. JDK 8 and 11 are no longer supported for this build.

Install Android SDK platform 30 and SDK Build Tools 35.0.0. ServerProxy uses
this repository's `gradle/serverproxy.gradle` configuration, so its upstream
Gradle 3.2.1 configuration is not used. Its source remains a pinned submodule.
Build the `flossDebug`
variant, or run:

```sh
# macOS / Linux
bash gradlew assembleFlossDebug
# Windows
.\gradlew.bat assembleFlossDebug
```

### ServerProxy dependency error

If Gradle reports `Could not resolve project :ServerProxy` with
`None of the consumable configurations have attributes`, check that
`ServerProxy/build.gradle` exists. An uninitialized submodule leaves Gradle with
an empty project and no Android library variants. Run the submodule command
above, then sync Gradle again.

APK build success does not establish Android 17 runtime compatibility; device
testing and further compatibility work are still required.

## Updating Icons
Media Icons are double standard size.  On https://romannurik.github.io/AndroidAssetStudio/icons-actionbar.html you can manually change this via the following js commands:
```
PARAM_RESOURCES.iconSize = {w: 64, h: 64}
PARAM_RESOURCES['targetRect-clipart'] = { x: 12, y: 12, w: 40, h: 40 }
form.fields_[0].params_.maxFinalSize = {w: 512, h: 512}
```
