<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Component-investigation Changelog

## Unreleased

## 1.0.10 - 2026-10-26

### Fixed

- **Binary compatibility with the 261/262 lines**: the Markdown export no longer opens the report
  via `FileEditorManager.getInstance`, which on 2026.3 resolves through a Kotlin companion object
  that doesn't exist on builds 261/262 (`NoSuchFieldError: FileEditorManager.Companion`). It now
  uses `OpenFileDescriptor(...).navigate(...)`, a plain platform API available across the whole
  `261`–`263.*` range.

## 1.0.9 - 2026-10-26

### Changed

- **Rider 2026.3 (build 263) support**: the plugin now builds against the Rider 2026.3 platform and
  its matching ReSharper/Rider SDK, and the compatibility range is extended through `263.*` (lower
  bound unchanged at `261`). 2026.3 is currently EAP-only, so the platform and backend SDK track the
  latest EAP build.

## 1.0.8 - 2026-06-25

### Fixed

- **WebForms markup usages**: class usages from ASP.NET WebForms markup (e.g. `<uc:SomeClass …/>`
  in `.aspx`/`.ascx`) are no longer dropped. References whose tree node reports an invalid document
  range (common for ASP / secondary PSI) now fall back to the node's navigation range, and a usage
  with a valid source file is always emitted (at line 1 if no range resolves) instead of being
  silently skipped.

## 1.0.7 - 2026-06-17

### Added

- **Group by File toggle**: a button in the Relations results' lower pane switches the layout
  between member-first (member → access type, the default) and file-first (file → member → access),
  so you can see which members of the component are touched in each file. Implemented with public
  API only (no internal-platform-API usage).

## 1.0.6 - 2026-06-17

### Fixed

- **"Wrong thread" exception on solution load**: the `showRelations` protocol subscription is now
  set up on the UI thread (`Dispatchers.EDT`) instead of the background startup coroutine, so the
  Relations lens wires up reliably without throwing `IllegalStateException` from `RdSignal.advise`.

### Changed

- Use the non-deprecated `com.intellij.openapi.rd.util.lifetime` accessor (one fewer deprecated-API
  usage).

## 1.0.5 - 2026-06-17

### Changed

- Maintenance re-publish of 1.0.4 (no functional changes) — carries forward the internal-platform-API
  removal and the `*.ascx.vb` navigation fix.

## 1.0.4 - 2026-06-17

### Fixed

- **VB usage navigation**: usages in files without real frontend PSI (e.g. `*.ascx.vb`) no longer
  jump to line 1. Navigation is now anchored on the backend-reported line (the same value the
  Markdown export uses), trusting the precise offset only when it lands on that line.

### Changed

- **Removed internal-platform-API usage** (flagged by JetBrains Marketplace). The custom
  "Group by Folder Tree" action is dropped in favour of the platform's built-in
  **Group by Directory Structure** toggle, which provides the same folder-tree / flat switch.

## 1.0.3 - 2026-06-17

### Added

- **Plugin logo**: a relations-graph icon (central class node linked to its usages) with light and
  dark theme variants, shown in the Plugins list and on the Marketplace listing.

## 1.0.2 - 2026-06-17

### Changed

- **Relations now shows external usages only**: usages located in the file(s) where the class is
  declared (including all `partial`-class files) are excluded, so the class's own internal
  self-references no longer clutter the results.

### Added

- **Relations Code Vision lens**: a "Relations" link next to Usages/Inheritors on any C#/F#/VB
  type. Clicking it finds all usages of the type *and of every member* (methods, properties,
  fields, constants, events) and shows them in the native Find Usages view, grouped by member
  then by access type (read / write / invocation / instantiation / type usage).
- ReSharper backend (`src/dotnet`) performing the analysis, wired to the frontend over the rd
  protocol (`protocol/`).
- **Folder Tree / Flat** toggle in the results' grouping toolbar.
- **Export to Markdown**: writes the full report — with a real usage example per call site and
  clickable file links — to `<solution>/.relations/`. Links in the Markdown preview navigate to
  the file at the line.
