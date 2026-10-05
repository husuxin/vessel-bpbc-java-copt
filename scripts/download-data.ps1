param([string]$InstanceName = 'L1-B10-V10-01.txt')
$ErrorActionPreference = 'Stop'
if ($InstanceName -notmatch '^L[12]-B(10|15)-V\d+-0[1-5]\.txt$') { throw '请使用作者仓库中的实例文件名。' }
$taskDataDir = Join-Path (Split-Path -Parent $PSScriptRoot) 'data\official'
New-Item -ItemType Directory -Force -Path $taskDataDir | Out-Null
$taskTarget = Join-Path $taskDataDir $InstanceName
Invoke-WebRequest -Uri "https://raw.githubusercontent.com/LingxiaoWu2021/VSPP/07fa8c226600bae30b4c39ba65da5523c1619723/data/$InstanceName" -OutFile $taskTarget
Get-FileHash -LiteralPath $taskTarget -Algorithm SHA256
