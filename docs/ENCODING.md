# Encoding Guide

This repository is configured to use UTF-8 by default.

## Project Rules

- `.editorconfig` enforces `charset = utf-8`
- `.gitattributes` enforces text line endings and marks binary files

## PowerShell (Windows)

Run this once per shell session if Korean text looks broken:

```powershell
.\scripts\dev\enable_utf8.ps1
```

## Recommended Git Settings (local)

```powershell
git config core.autocrlf input
git config core.safecrlf true
```
