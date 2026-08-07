# Project Build & Dev Misc Tools

Command-line tool for managing development projects with AI template support.

## Features

- **Project Management**: Quick navigation and opening of projects across organizations
- **Git Integration**: Clone repositories with automatic directory structure
- **PRP Management**: Built-in support for Project Requirement Prompts (PRPs)
- **JavaFX UI**: Modern graphical interface for PRP management

## PRP Templates

This repository hosts PRP (Project Requirement Prompts) templates for use with AI coding assistants.

### Available Templates

- [PRPTemplate-v2026-01-29_2045Z-klwXbO.md](prp-templates/PRPTemplate-v2026-01-29_2045Z-klwXbO.md) - Current default template (UTC timestamp, CRC32 verified)

### Using Templates

You can reference these templates in your `Settings.xml`:

```xml
<prpTemplate src="https://littlejlib.github.io/proj/prp-templates/PRPTemplate-v2026-01-29_2045Z-klwXbO.md" />
```

The tool will automatically download and cache the template locally for offline use.

## Documentation

See the [README](https://github.com/littlejlib/proj) for full documentation.

## About PRP Convention

The PRP (Project Requirement Prompts) convention is based on work by Cole Medin. See his video: [Project Requirement Prompts](https://www.youtube.com/watch?v=Mk87sFlUG28).

## Repository

Visit the [GitHub repository](https://github.com/littlejlib/proj) for source code and releases.
