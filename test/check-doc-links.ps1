$ErrorActionPreference = 'Stop'

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$excludedDirectories = '\\(.git|node_modules|target|dist|ResearchAssistantData)\\'
$brokenLinks = @()

$markdownFiles = Get-ChildItem -Path $projectRoot -Recurse -File -Filter '*.md' |
    Where-Object { $_.FullName -notmatch $excludedDirectories }

foreach ($file in $markdownFiles) {
    $content = Get-Content -LiteralPath $file.FullName -Raw -Encoding UTF8
    $matches = [regex]::Matches($content, '\[[^\]]*\]\(([^)]+)\)')

    foreach ($match in $matches) {
        $target = $match.Groups[1].Value.Trim()
        if ($target -match '^(https?://|mailto:|#)') {
            continue
        }

        $target = $target.Trim('<', '>')
        $target = ($target -split '#')[0]
        if ([string]::IsNullOrWhiteSpace($target)) {
            continue
        }

        $resolvedTarget = Join-Path $file.DirectoryName $target
        if (-not (Test-Path -LiteralPath $resolvedTarget)) {
            $brokenLinks += [pscustomobject]@{
                File = $file.FullName.Substring($projectRoot.Length + 1)
                Target = $match.Groups[1].Value
            }
        }
    }
}

if ($brokenLinks.Count -gt 0) {
    $brokenLinks | Format-Table -AutoSize
    throw "Found $($brokenLinks.Count) broken Markdown relative links."
}

Write-Output "Markdown link check passed: $($markdownFiles.Count) files scanned."
