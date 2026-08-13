# DiskScope

DiskScope is a Windows desktop storage analyzer built with Java 21 and JavaFX. It scans local drives, shows where storage is being used, classifies files and folders by safety category, and helps review cleanup candidates without exposing protected system files as normal delete targets.

The project started as a command-line disk scanner and now includes a polished JavaFX interface with drive overview stats, breadcrumb navigation, safe cleanup views, and confirmation-based Recycle Bin deletion.

## Features

- Drive overview with total, used, free, and used-percentage storage.
- Fast NTFS scan mode using Windows volume access and JNA.
- Java fallback scanner when fast NTFS access is unavailable.
- Background scanning so the JavaFX UI does not freeze.
- Breadcrumb navigation for browsing folders.
- Browse, Largest Files, and Cleanup tabs.
- Storage classification for user data, applications, developer tools, temporary data, recycle bin, unknown files, and protected system paths.
- Protected system items are clearly labeled and cannot be deleted from the UI.
- Delete flow moves items to the Windows Recycle Bin after a confirmation dialog showing name, full path, size, and category.
- Admin launcher for fast NTFS scanning.

## Screenshots

Add screenshots or a short GIF in the `screenshots/` folder before publishing the repository.

Recommended captures:

- `screenshots/dashboard.png` - main DiskScope dashboard after a scan.
- `screenshots/loading.png` - Pac-Man scan loading state.
- `screenshots/delete-confirmation.png` - Recycle Bin confirmation dialog.
- `screenshots/cleanup-view.gif` - optional short GIF switching between Browse, Largest Files, and Cleanup.

## Why Administrator Mode Is Needed

DiskScope has two scan modes:

- `FAST_NTFS` opens the Windows volume directly, such as `\\.\C:`, to build a fast NTFS index.
- `JAVA_FALLBACK` uses normal Java file traversal when direct volume access is denied or unavailable.

Windows blocks direct volume access for normal user processes. If DiskScope is not run as administrator, the fast scanner may fail with Windows error `5` and the app will automatically use the slower Java fallback.

Run the admin launcher when you want the fast scanner:

```powershell
Run-DiskScope-Admin.bat
```

Then press `Rescan drive` in the app. A successful fast scan will show `FAST_NTFS` in the status pill.

## How To Run

Requirements:

- Windows
- Java 21
- Maven

From the project folder:

```powershell
mvn javafx:run
```

For fast NTFS scanning, use:

```powershell
Run-DiskScope-Admin.bat
```

The admin launcher sets the Java 21 and Maven paths used on this machine, then starts the JavaFX app with Windows administrator privileges.

## Safety Rules

DiskScope is designed to avoid dangerous cleanup actions:

- Protected system paths are labeled `System / Protected`.
- Protected items never receive an enabled destructive button.
- Unknown items are not shown as cleanup suggestions.
- Recycle Bin paths are not treated as normal delete targets.
- Any destructive action must be confirmed before execution.
- Deletion uses the Windows Recycle Bin, not permanent deletion.

Examples of protected items include:

- `pagefile.sys`
- `hiberfil.sys`
- `swapfile.sys`
- `Windows`
- `System Volume Information`
- `Recovery`
- Program Files and other protected Windows locations

## Tech Stack

- Java 21
- JavaFX
- Maven
- JNA / JNA Platform
- Windows NTFS APIs

## Project Structure

```text
DiskScope/
├── src/
├── screenshots/
├── pom.xml
├── README.md
├── .gitignore
├── Run-DiskScope-Admin.bat
└── Run-DiskScope.ps1
```

Generated build output such as `target/`, `out/`, `.class` files, `.jar` files, and IDE metadata should not be committed.

## Status

DiskScope is Windows-only because its fast scanner depends on NTFS and Windows volume APIs. The Java fallback scanner keeps the app usable without admin access, but the fast mode is the main performance feature.
