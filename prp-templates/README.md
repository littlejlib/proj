# PRP Templates

This directory contains PRP (Project Requirement Prompts) templates for use with the Project Build & Dev Misc Tools.

## About PRP Templates

PRP templates define the structure and guidelines for creating Project Requirement Prompt files. These files are used with AI coding assistants (like Claude Code) to manage project requirements in a structured way.

## Available Templates

### PRPTemplate-v2026-01-29_2045Z-klwXbO.md

Current default template that includes:
- PRP number and name placeholders
- Usage guidelines for AI coding agents
- Instructions for file organization
- Guidelines for status tracking
- Best practices for logging and indexing

## Using Templates

### In the Tool

The tool automatically uses templates configured in your `Settings.xml`:

```xml
<prpTemplate src="https://littlejlib.github.io/proj/prp-templates/PRPTemplate-v2026-01-29_2045Z-klwXbO.md" />
```

### Custom Templates

You can create your own template by:

1. Copying an existing template
2. Modifying it to your needs
3. Hosting it online or saving it locally
4. Updating your `Settings.xml` to point to your custom template

### Placeholders

Templates support the following placeholders:
- `%index%` - Replaced with the PRP number (e.g., "01", "02", etc.)
- `%name%` - Replaced with the PRP name provided by the user

## Template Versioning

Templates are versioned using:
- **Timestamp**: UTC date and time in format `vYYYY-MM-DD_HHMMZ` (Z indicates UTC)
- **CRC32 Checksum**: Base52 encoded checksum after dash `-xxxxxx` for integrity verification

Example: `PRPTemplate-v2026-01-29_2045Z-klwXbO.md`

### Creating Versioned Templates

Use the `PRPTemplateVersioner.java` utility in this folder:

```bash
java PRPTemplateVersioner.java prptemplate_evolving_draft.md
```

This will create a versioned copy with UTC timestamp and CRC32 checksum.

## Contributing

If you create an improved template that might benefit others, feel free to submit a pull request to add it to this collection.
