[CmdletBinding(PositionalBinding=$false)]
param([string]$CoptHome = $env:COPT_HOME, [Parameter(ValueFromRemainingArguments=$true)][string[]]$RunArgs)
$ErrorActionPreference = 'Stop'
& (Join-Path $PSScriptRoot 'build.ps1') -CoptHome $CoptHome
$taskEntry = 'Main'
if ($RunArgs.Count -gt 0 -and $RunArgs[0] -eq 'verify') { $taskEntry = 'Verify'; $RunArgs = if ($RunArgs.Count -gt 1) { $RunArgs[1..($RunArgs.Count-1)] } else { @() } }
$taskJar = Join-Path $CoptHome 'lib\copt.jar'
$taskBin = Join-Path $CoptHome 'bin'
& java "-Djava.library.path=$taskBin" -cp "$taskJar;$(Join-Path $PSScriptRoot 'build')" $taskEntry @RunArgs
if ($LASTEXITCODE -ne 0) { throw '运行失败，见上述诊断。' }
