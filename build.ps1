param([string]$CoptHome = $env:COPT_HOME)
$ErrorActionPreference = 'Stop'
if (-not $CoptHome) { throw '请设置 COPT_HOME，或传入 -CoptHome 安装目录。' }
$taskJar = Join-Path $CoptHome 'lib\copt.jar'
if (-not (Test-Path -LiteralPath $taskJar)) { throw "未找到 $taskJar" }
$taskBuild = Join-Path $PSScriptRoot 'build'
New-Item -ItemType Directory -Force -Path $taskBuild | Out-Null
$taskSources = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'src'),(Join-Path $PSScriptRoot 'test') -Filter '*.java' -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName })
& javac -encoding UTF-8 -cp $taskJar -d $taskBuild @taskSources
if ($LASTEXITCODE -ne 0) { throw 'Java 编译失败。' }
