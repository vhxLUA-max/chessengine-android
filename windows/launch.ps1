param(
    [string]$Engine = $env:CHEEZIE_STOCKFISH
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($Engine)) {
    $Engine = "stockfish.exe"
}

python "$PSScriptRoot\cheezie_windows.py" --engine $Engine
