<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Component-investigation Changelog

## Unreleased

## 1.0.1 - 2026-06-17

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
