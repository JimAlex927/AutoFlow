# Codex Build Notes

This file records the Android build commands and pitfalls that were verified in this repo, so a future Codex session can continue without rediscovering them.

## Shell Requirement

- Always use PowerShell 7.x.
- Do not use Windows built-in PowerShell 5.x.

Verified version in this workspace:

```powershell
& 'C:\Program Files\PowerShell\7\pwsh.exe' -NoProfile -Command '$PSVersionTable.PSVersion.ToString()'
```

## Working Compile Command

This command successfully compiled `:app:compileDebugJavaWithJavac`.

```powershell
& 'C:\Program Files\PowerShell\7\pwsh.exe' -NoProfile -Command '& {
    Set-Location ''C:\Users\1\Desktop\projects\AutoFlow\AutoFlow''
    $root = (Get-Location).Path
    $env:JAVA_HOME = ''C:\Program Files\Android\Android Studio\jbr''
    $env:GRADLE_USER_HOME = Join-Path $root ''.gradle-home-check''
    $env:ANDROID_SDK_HOME = $root
    $env:USERPROFILE = $root
    $env:HOME = $root
    $env:Path = ''C:\Program Files\Android\Android Studio\jbr\bin;'' + $env:Path
    .\gradlew.bat :app:compileDebugJavaWithJavac --no-daemon --console=plain
}'
```

Result seen on 2026-04-16:

- `BUILD SUCCESSFUL`

## Release Build Command

This is the command used to verify `release` build configuration:

```powershell
& 'C:\Program Files\PowerShell\7\pwsh.exe' -NoProfile -Command '& {
    Set-Location ''C:\Users\1\Desktop\projects\AutoFlow\AutoFlow''
    $root = (Get-Location).Path
    $env:JAVA_HOME = ''C:\Program Files\Android\Android Studio\jbr''
    $env:GRADLE_USER_HOME = Join-Path $root ''.gradle-home-check''
    $env:ANDROID_SDK_HOME = $root
    $env:USERPROFILE = $root
    $env:HOME = $root
    $env:Path = ''C:\Program Files\Android\Android Studio\jbr\bin;'' + $env:Path
    .\gradlew.bat :app:assembleRelease --no-daemon --console=plain
}'
```

Important:

- Reuse `.gradle-home-check`.
- A separate `.gradle-home-release-check` triggered a Gradle generated-accessor failure with `AccessDeniedException` on a wrapper jar file.

## Current Release Configuration

As of 2026-04-16, `release` was intentionally changed to match the conservative behavior of `C:\Users\1\Desktop\tmp\demo2`:

- `isMinifyEnabled = false`
- `isShrinkResources = false`
- `proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")`
- `signingConfig = signingConfigs.getByName("debug")`

Reason:

- The project had release-only issues.
- `demo2` uses a safer release setup with shrinking/obfuscation disabled.
- Debug signing was kept so local release APKs remain directly installable.

## Current Release Blocker

The `release` Gradle configuration now parses and runs far enough to reach Android SDK checks, but `:app:assembleRelease` is still blocked by local SDK environment issues:

- Android SDK Build-Tools 36 license not accepted
- Android SDK Platform 36 license not accepted
- `C:\Users\1\AppData\Local\Android\Sdk\platforms\android-36\package.xml` had access denied / parse failure during the build

Observed Gradle guidance:

```text
sdkmanager --licenses
```

If a future session wants to finish `release` packaging, check SDK Manager state before changing app code again.

## Command Pitfalls

- Do not build with Windows PowerShell 5.x.
- When using nested `pwsh -Command` from Codex, quote the inner script carefully. Bad quoting caused commands to hang or split `PATH` incorrectly.
- Prefer wrapping the inner script as `& { ... }`.
- Keep `GRADLE_USER_HOME` inside the workspace to avoid permission problems with global Gradle directories.
- `compileDebugJavaWithJavac` succeeded with the environment above, so if `release` fails immediately, compare against this exact command first.

## Workspace Notes

- Temporary Gradle homes may appear in the repo root, for example `.gradle-home-check`.
- Do not delete workspace files broadly. If cleanup is needed, only remove known temporary directories created for build verification.
