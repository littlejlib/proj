-- proj.lua -- Clink integration for proj.exe (cmd.exe directory navigation)
--
-- Install:  proj --init clink > "%LOCALAPPDATA%\clink\proj.lua"
--           then open a new cmd window. That directory is already on Clink's
--           script path (check with `clink info`), and Clink is already injected
--           via HKCU\Software\Microsoft\Command Processor\AutoRun -- so there is
--           no AutoRun edit and no per-session setup.
--
-- Mechanism: rewrite the input line so CMD ITSELF performs the cd. This is exactly
-- how Clink's own directory shortcuts work -- see make_cd_command() in
-- clink/lib/src/intercept.cpp. Nothing is injected into another process and no
-- keystrokes are stuffed anywhere; the shell moves itself, which is the only thing
-- that actually works.
--
-- Contract this relies on (proj.exe honours it via StdoutChannel):
--   stdout = resolved absolute path, backslash-separated, and NOTHING else
--   stderr = every human-facing byte (picker UI, prompts, logs, errors)
--   empty stdout = cancelled -> for /f iterates zero times -> no cd, no error

local TOOL = "proj.exe"
local VERB = "proj"

-- ONLY the navigating invocations get wrapped. This is a whitelist on purpose, not a
-- blacklist: query commands legitimately answer on stdout (`proj files --paths` exists to
-- be piped into ripgrep), and wrapping one of those would capture its output and cd to the
-- last line of it. Anything not listed here passes through to CMD untouched, which is the
-- safe direction to be wrong in -- at worst you get no cd and a hint on stderr.
local NAV = { [""] = true, ["o"] = true, ["open"] = true }

local function filter(line)
    local verb, rest = line:match("^%s*([^%s]+)%s*(.-)%s*$")
    if not verb or verb:lower() ~= VERB then return end

    -- Leave the line alone when the user is doing their own plumbing. Wrapping it would
    -- move their redirection inside the backquotes, so `proj --init clink > proj.lua`
    -- would write nothing and capture the adapter as a path.
    if rest:find("[>|<]") then return end
    if not NAV[rest:lower()] then return end

    -- Hand the work back to CMD at the normal command position rather than running
    -- the picker from inside this hook: for /f captures stdout (the path), stderr
    -- still reaches the console so the UI renders, and cd runs in the real shell.
    -- Single % is correct -- this is an interactive line, not a batch file.
    return 'for /f "usebackq delims=" %i in (`' .. TOOL .. ' --print-cd ' .. rest .. '`) do @cd /d "%i"'
end

clink.onfilterinput(filter)
