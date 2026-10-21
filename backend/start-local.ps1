$ErrorActionPreference = 'Stop'
& node (Join-Path $PSScriptRoot 'start.cjs')
if ($LASTEXITCODE -ne 0) { throw 'Backend could not start. Review the message above.' }
