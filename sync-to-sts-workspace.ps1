$ErrorActionPreference = "Stop"

$source = Split-Path -Parent $MyInvocation.MyCommand.Path
$target = "D:\STSWorkspace\29Jul2026\railway-signaling-diagram-processor"

New-Item -ItemType Directory -Force -Path $target | Out-Null

robocopy $source $target /MIR /XD .git target /XF *.log
$exitCode = $LASTEXITCODE

if ($exitCode -le 7) {
    Write-Host "Project synced to $target"
    exit 0
}

throw "Robocopy failed with exit code $exitCode"
