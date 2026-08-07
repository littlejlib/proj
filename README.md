# Project Build & Dev Tools

Command-line tool for managing development projects with AI template support.

## Why This Tool?

This tool addresses common pain points in multi-project development environments:

- **Simplified Project Navigation**: Quickly open projects across multiple organizations without remembering paths
- **Consistent Git Workflow**: Clone repositories to the correct directory structure automatically
- **AI-Friendly Project Management**: Built-in support for Project Requirement Prompts (PRPs) to work seamlessly with AI coding assistants
- **Terminal Integration**: Change directory in the current terminal after project selection (via batch wrapper)

## About the PRP Convention

The PRP (Project Requirement Prompts) convention is based on videos and discussions by Cole Medin (GitHub: [coleam00](https://github.com/coleam00), YouTube: [@ColeMedin](https://www.youtube.com/@ColeMedin)). See his video: [Project Requirement Prompts](https://www.youtube.com/watch?v=Mk87sFlUG28).

This is my implementation and might differ from Cole's approach. I don't completely follow his workflow—the PRP thought process made sense to me, and I use it daily. So much so that I made this simple tool. I neither endorse nor promote his videos/content, but the PRP concept is due credit to him and his ecosystem, which is why I'm mentioning it. I'm also providing this reference so people understand why this PRP convention exists, what problems it solves, and how to use PRPs effectively.

I have my own reasons for using PRPs, but to highlight one key benefit: it's better not to have status in the same file as instructions—it gets messy quickly and consumes more tokens. Having read-only PRPs and AI-only status files keeps things clear. Using multiple files instead of one file with all project requirements is also beneficial. It makes it possible to finish one feature before hitting context limits. Even if you exceed limits, the status report helps in resuming after compaction or clearing the conversation. These small benefits add up. I felt it was too much to explain here, so I'm pointing you to Cole's video. His conventions and commentary might differ from mine—my approach is independently inspired, but he and his ecosystem are the source.

## Requirements

- Java 11 or higher
- Windows, Linux, or macOS
- Maven (for building from source)

## Installation

### Option 1: Download Pre-built Release

1. Download the latest release ZIP from the `releases/` folder
2. Extract `proj.bat` and `littlejlib-proj-1.0.jar`
3. Place them in a directory of your choice
4. Run `proj.bat` (Windows)

### Option 2: Build from Source

1. Clone this repository
2. Run the release build script:
   ```bash
   make-release.bat
   ```
   This will:
   - Build the project with Maven
   - Create a timestamped release ZIP in `releases/` folder
   - Package both `proj.bat` and the shaded JAR

3. Extract the ZIP and use `proj.bat` to run the tool with directory-change support

## Usage

Run the tool using:
```bash
proj.bat
```

Or directly with Java:
```bash
java -jar littlejlib-proj-1.0.jar
```

### First Run

On first run, the tool creates a settings file at:
```
<user-home>/littlejlib/proj/Settings.toml
```

Edit it by hand, or run `proj settings` for a window that edits the same file. An existing
`Settings.xml` from an older version is migrated automatically on first run and kept alongside as
`Settings.xml.migrated`.

Configure:
- Code base directories, and whether each is primary, mirror or auxiliary
- Terminal and IDE launchers
- The command that starts a coding agent on a PRP
- Organizations with their Maven groupIds

### Main Features

**1. Open Project**
- List projects from configured organizations
- Sort alphabetically or by recent modification
- Open in: current terminal (with dir change), new CMD window, file explorer, NetBeans, or default editor

**2. Open Project (All Organizations)**
- Discover and open projects from any organization in your code repos directory
- Useful for exploring projects outside your configured list

**3. Git Clone Repository**
- Routes clones by organization: repos from a **configured** organization (your own
  projects) go into the code repos directory; any **other** organization is treated as
  external and goes into the GitHub mirror directory (falls back to code repos if the
  mirror path is not set)
- Format: `<code-repos | github-mirror>/<organization>/<repository-name>`
- Creates organization directories as needed

**4. Create New Project**
- Interactive wizard for new project creation
- Suggests Maven groupId based on organization
- Creates directory structure ready for initialization

**5. AI Template Management**
- Create and manage Project Requirement Prompts (PRPs)
- View PRP details and status directly in the terminal
- Open PRPs in your editor
- Close/reopen PRPs to track implementation status

**6. Settings Management**
- Edit settings without manually opening the XML file
- Add/edit/remove organizations
- Configure paths and tools

## Settings.toml Structure

Flat TOML: one setting per line, no sections. Values in `'single quotes'` are **literal** — no
escape processing at all — which is why a Windows path is written once, as itself. A value that
itself contains a single quote is wrapped in `'''three'''` instead. Nothing here ever needs a
doubled backslash.

```toml
scanDepth = 5
singleFileEditor = 'C:\user\Apps\VScode\code.exe'

# Directories holding many repositories. Only primary is shown by default and
# watched for live changes; the others stay indexed and searchable behind a
# checkbox in the picker. Move a path between lines to retype it.
codebase.primary   = ['C:\user\code']
codebase.mirror    = ['C:\user\github-mirror']
codebase.auxiliary = []

# %PATH% is the directory; %SCRIPT% is a generated script that cds there first.
# An empty run means that terminal can open a shell but not start a command.
terminal.Tabby.open = '"C:\user\Apps\tabby\Tabby.exe" open "%PATH%"'
terminal.Tabby.run  = ''
defaultTerminal = 'Tabby'

# One "open in" button per line in the picker.
ide.NetBeans = 'C:\user\Apps\netbeans\bin\netbeans64.exe "--open" "%PATH%"'

# Starting a coding agent on a PRP. The working directory is always the PROJECT
# ROOT, never the prp folder. %PRP% is relative to it; %PRP_ABS% is absolute.
agentCommand = 'claude "@%PRP% read this PRP and let us begin"'

prpTemplateSrc = 'https://littlejlib.github.io/proj/prp-templates/PRPTemplate.md'

# Directory name under a code base -> the maven groupId it publishes under.
org.xyz-jphil = 'io.github.xyz-jphil'

statusTemplate = '''
PRP Number: %index%
PRP Name: %name%
Status: IN PROGRESS
'''
```

### Key Configuration Options

- **codebase.primary / .mirror / .auxiliary**: directories holding many repositories. All are
  indexed and searchable; only primary is shown by default and live-watched.
- **terminal.NAME.open / .run**: how to open a shell somewhere, and how to run a command there.
  `proj term --which` reports which terminal is used for each — they are not always the same one.
- **defaultTerminal**: which of them to prefer. Blank means the best one detected.
- **ide.NAME**: an "open in" button per entry in the picker. Omit them all and the picker offers
  whatever it detects on the machine.
- **agentCommand**: what `proj prp agent <n>` runs, from the project root.
- **singleFileEditor**: editor for PRPs, poms and this file. Blank means whatever is installed.
- **org.NAME**: maven groupId for projects under that directory name.
- **scanDepth**: how many levels below a code base the scanner descends.

## Directory Structure

Expected project layout:
```
<CodeReposPath>/
├── organization-1/
│   ├── project-1/
│   ├── project-2/
│   └── ...
├── organization-2/
│   ├── project-a/
│   ├── project-b/
│   └── ...
└── ...
```

## License

This project is intended for personal or organizational use in development workflows.
