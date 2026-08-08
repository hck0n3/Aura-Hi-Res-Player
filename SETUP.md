# Setup Instructions

This document provides instructions for setting up the Aura Hi-Res Player project for development.

## Windows — new PC or formatted disk (fast path)

**Before formatting**, back up secrets outside the repo:

```powershell
.\scripts\backup-dev-secrets.ps1
```

Copy `%USERPROFILE%\AuraHiResDevBackup` to USB or cloud.

**On the new machine** (after cloning the repo):

```powershell
.\scripts\setup-dev-environment.ps1
```

That single script restores the backup (if present), configures the Android SDK path, writes
`local.properties`, loads signing from `app/keystore/CREDENTIALS.txt`, installs Cursor extensions,
fixes the Java/Buildship IDE error, and runs the signing verification.

Then in Cursor once: `Java: Clean Java Language Server Workspace` → Reload Window.

**Before publishing to all users:**

```powershell
.\scripts\pre-publish-check.ps1 -Build
```

Push a stable tag `vX.Y.Z` (no `-beta`). GitHub Actions builds and publishes; secrets live in GitHub,
not on disk.

---

## Prerequisites

- Android Studio (latest version recommended)
- Android SDK (API level as specified in `build.gradle.kts`)
- JDK 21
- Git

## Initial Setup

### 1. Clone the Repository

```bash
git clone https://github.com/hck0n3/Aura-Hi-Res-Player.git
cd Aura-Hi-Res-Player
```

### 2. Configure Local Properties

Create a `local.properties` file from the template:

```bash
cp local.properties.template local.properties
```

Edit `local.properties` and set your Android SDK path:

```properties
sdk.dir=/path/to/your/android/sdk
```

**Example paths:**

- macOS: `/Users/username/Library/Android/sdk`
- Linux: `/home/username/Android/sdk`
- Windows: `C:\\Users\\username\\AppData\\Local\\Android\\sdk`

### 3. Configure Firebase (Optional — off by default, and off in the published app)

**The app we publish has no Firebase.** There is no `google-services.json` in this repository, and the release pipeline does not add one, so the Firebase Gradle plugins (`app/build.gradle.kts`, the `hasGoogleServicesConfig` gate) are never applied and Firebase is never initialised — Crashlytics and Analytics collect and send nothing. Firebase Analytics is not referenced anywhere in the code at all; only Crashlytics is wired up, through `CrashReporter` (a no-op in the `foss` flavor, Crashlytics in `gms`).

If **you** want crash reporting **in your own build**, you can opt in:

1. Create a Firebase project at [Firebase Console](https://console.firebase.google.com/)
2. Add an Android app to your Firebase project, using the app's applicationId (`iad1tya.echo.music`)
3. Download the `google-services.json` file
4. Place it in the `app/` directory
5. Build a `gms` variant (e.g. `assembleUniversalGmsRelease`) — the `foss` variant has no crash backend, so it stays a no-op regardless

Adding the file applies the Google Services + Crashlytics plugins, which initialises Firebase: your build will then send crash reports **to your own Firebase project**, and Firebase Analytics will begin its automatic collection even though no code calls it. Do this knowingly — and never commit the file (it is gitignored).

**Note:** If you skip Firebase setup, the app builds and runs normally. This is the default and the shipped configuration.

### 4. Configure Release Signing (Optional)

For release builds, you need to configure signing credentials. Set these as environment variables or in `gradle.properties`:

```bash
# Environment variables
export KEYSTORE_PATH=/path/to/your/keystore.jks
export STORE_PASSWORD=your_store_password
export KEY_ALIAS=your_key_alias
export KEY_PASSWORD=your_key_password
```

Or add to `gradle.properties` (never commit this file):

```properties
KEYSTORE_PATH=/path/to/your/keystore.jks
STORE_PASSWORD=your_store_password
KEY_ALIAS=your_key_alias
KEY_PASSWORD=your_key_password
```

### 5. Build the Project

Open the project in Android Studio or build from the command line.

**For FOSS variants (without Google Cast):**
```bash
# Debug build
./gradlew assembleUniversalFossDebug

# Release build (requires signing configuration)
./gradlew assembleUniversalFossRelease
```

**For GMS variants (with Google Cast):**
```bash
# Debug build
./gradlew assembleUniversalGmsDebug

# Release build (requires signing configuration)
./gradlew assembleUniversalGmsRelease
```

*(On Windows, use `.\gradlew.bat` instead of `./gradlew`)*

### 6. Configure AI Translation (Optional)

Aura Hi-Res Player supports AI-powered lyrics translation. You can configure this in **Settings -> AI Settings**.

#### Option A: Using OpenRouter (Default)

This is the recommended setup for most users.

1. Get an API Key from [OpenRouter](https://openrouter.ai/).
2. In the app, go to **Settings -> AI Settings**.
3. Ensure **Provider** is set to **OpenRouter**.
4. Enter your **API Key**.

#### Option B: Using Custom Provider

Use this for other services like OpenAI, Anthropic, or local LLMs.

1. In the app, go to **Settings -> AI Settings**.
2. Select your **Provider** (e.g., ChatGPT, Gemini, or Custom).
3. If using **Custom**, enter your provider's **Base URL**.
4. Enter your **API Key**.

## Important Files

### Confidential Files (Never commit these)

- `local.properties` - Contains your local SDK path
- `app/google-services.json` - Contains Firebase credentials
- `*.keystore` - Contains signing keys for release builds
- `gradle.properties` - May contain signing credentials

These files are already listed in `.gitignore` and should never be committed to version control.

### Template Files (Safe to commit)

- `local.properties.template` - Template for local properties
- `app/google-services.json` - Optional Firebase configuration

## Troubleshooting

### Build Fails with "SDK location not found"

Make sure you've created `local.properties` with the correct SDK path.

### Firebase-related Build Errors

If you're not using Firebase, you can build the standard debug variant without `app/google-services.json`:

```bash
./gradlew assembleUniversalFossDebug
```

### Gradle Sync Issues

Try cleaning and rebuilding:

```bash
./gradlew clean
./gradlew build
```

## Contributing

Please read [CONTRIBUTING.md](CONTRIBUTING.md) for details on our code of conduct and the process for submitting pull requests.

## License

This project is licensed under the GNU General Public License v3.0 - see the [LICENSE](LICENSE) file for details.
