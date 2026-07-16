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

As of 2026-07-15, `release` uses a conservative first-stage R8 configuration:

- `isMinifyEnabled = true`
- `isShrinkResources = false`
- R8 optimization is disabled in `proguard-rules.pro`
- Gson-persisted field names and Android entry-point class names are preserved
- Release signing is loaded from ignored `keystore.properties`; Debug signing is never used

`:app:assembleRelease` completed successfully with JDK 17. R8 generated APK splits and `app/build/outputs/mapping/release/mapping.txt`. Without `keystore.properties`, the outputs are intentionally unsigned and are only suitable for build verification.

See `docs/RELEASE_GUIDE.md` for keystore setup, CI Secrets and the required device regression checklist.

## Command Pitfalls

- Do not build with Windows PowerShell 5.x.
- When using nested `pwsh -Command` from Codex, quote the inner script carefully. Bad quoting caused commands to hang or split `PATH` incorrectly.
- Prefer wrapping the inner script as `& { ... }`.
- Keep `GRADLE_USER_HOME` inside the workspace to avoid permission problems with global Gradle directories.
- `compileDebugJavaWithJavac` succeeded with the environment above, so if `release` fails immediately, compare against this exact command first.

## Workspace Notes

- Temporary Gradle homes may appear in the repo root, for example `.gradle-home-check`.
- Do not delete workspace files broadly. If cleanup is needed, only remove known temporary directories created for build verification.
