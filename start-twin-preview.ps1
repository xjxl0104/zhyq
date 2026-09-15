param([switch]$Layout)
$ErrorActionPreference = 'Stop'
$frontendDirectory = Join-Path $PSScriptRoot 'frontend'
$viteEntry = Join-Path $frontendDirectory 'node_modules/vite/bin/vite.js'
if (-not (Test-Path -LiteralPath $viteEntry)) {
    throw 'Please install frontend dependencies first: cd frontend; pnpm install'
}
$nodeCommand = Get-Command node -ErrorAction SilentlyContinue
if ($nodeCommand) {
    $nodeExecutable = $nodeCommand.Source
} else {
    $nodeExecutable = Join-Path $env:USERPROFILE '.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node.exe'
    if (-not (Test-Path -LiteralPath $nodeExecutable)) { throw 'Node.js is required.' }
}
$previewPort = if ($Layout) { 5323 } else { 5273 }
$previewPath = if ($Layout) { '/dev/twin-layout.html' } else { '/twin-preview' }
Write-Host "Warehouse preview: http://127.0.0.1:$previewPort$previewPath"
Push-Location -LiteralPath $frontendDirectory
try { & $nodeExecutable $viteEntry --host 127.0.0.1 --port $previewPort --strictPort }
finally { Pop-Location }
