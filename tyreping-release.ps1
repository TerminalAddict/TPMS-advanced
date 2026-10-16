#requires -Version 5.1
<#
.SYNOPSIS
    Build, sign, tag, and publish a TyrePing Android release.

.DESCRIPTION
    Run this file from the root of the TyrePing repository, or invoke it by path
    from another directory. The script uses its own directory as the Git root.

    Safeguards:
      * Requires the clean, current 'tyreping' branch.
      * Only changes gradle/libs.versions.toml in the repository.
      * Runs unit tests, builds the release APK and verifies the APK signature.
      * Checks the package ID, release version and version code.
      * Verifies the permanent TyrePing signing certificate, not just 'signed'.
      * Makes an annotated tag and publishes through authenticated GitHub CLI.
      * Publishes as a pre-release unless -Stable is supplied.
      * Does not read, display, upload, or commit the contents of secrets/.
      * Supports -DryRun and -Resume for interrupted publication.

.EXAMPLE
    .\tyreping-release.ps1 -Version 0.4.0 -DryRun

.EXAMPLE
    .\tyreping-release.ps1 -Version 0.4.0

.EXAMPLE
    .\tyreping-release.ps1 -Version 0.4.0 -NotesFile D:\ReleaseNotes\v0.4.0.md

.EXAMPLE
    .\tyreping-release.ps1 -Version 0.4.0 -Resume

.EXAMPLE
    .\tyreping-release.ps1 -Version 1.0.0 -Stable
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^\d+\.\d+\.\d+$')]
    [string]$Version,

    [string]$NotesFile,
    [string]$OutputRoot,
    [switch]$Stable,
    [switch]$DryRun,
    [switch]$Resume,
    [switch]$Yes
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repository = 'TerminalAddict/TyrePing'
$expectedPackage = 'nz.paulwillard.tyreping'
$expectedCertificateSha256 = 'c486ced0cc11ae5f46b59ae10520f227768363f7db34c3474d472b7f0d405f70'
$branch = 'tyreping'
$tag = "v$Version"
$repoRoot = [System.IO.Path]::GetFullPath($PSScriptRoot)
$versionFile = Join-Path $repoRoot 'gradle\libs.versions.toml'
$gradlew = Join-Path $repoRoot 'gradlew.bat'
$metadataFile = Join-Path $repoRoot 'app\phone\build\outputs\apk\release\output-metadata.json'
$apkDirectory = Join-Path $repoRoot 'app\phone\build\outputs\apk\release'
$keystore = Join-Path $repoRoot 'secrets\app-keystore'
$keysFile = Join-Path $repoRoot 'secrets\keys.json'

if ([string]::IsNullOrWhiteSpace($OutputRoot)) {
    $OutputRoot = Join-Path (Split-Path $repoRoot -Parent) 'TyrePing-Releases'
}
$OutputRoot = [System.IO.Path]::GetFullPath($OutputRoot)
$releaseDir = Join-Path $OutputRoot $Version
# Release artifacts belong outside the Git worktree.
$repoPrefix = $repoRoot.TrimEnd([char[]]@('\', '/')) + [System.IO.Path]::DirectorySeparatorChar
if ($OutputRoot -ieq $repoRoot -or
    $OutputRoot.StartsWith($repoPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw 'OutputRoot must be outside the TyrePing Git checkout.'
}
$releaseApk = Join-Path $releaseDir "TyrePing-v$Version.apk"
$checksumFile = Join-Path $releaseDir 'SHA256SUMS.txt'
$generatedNotesFile = Join-Path $releaseDir 'RELEASE_NOTES.md'

function Assert-LastExit([string]$Operation) {
    if ($LASTEXITCODE -ne 0) {
        throw "$Operation failed (exit code $LASTEXITCODE)."
    }
}

function Invoke-NativeChecked {
    param(
        [string]$Description,
        [string]$Command,
        [string[]]$Arguments
    )
    Write-Host "`n==> $Description" -ForegroundColor Cyan
    & $Command @Arguments
    Assert-LastExit $Description
}

function Get-ApkSigner {
    $sdkCandidates = @(
        $env:ANDROID_HOME,
        $env:ANDROID_SDK_ROOT,
        $(if ($env:LOCALAPPDATA) { Join-Path $env:LOCALAPPDATA 'Android\Sdk' })
    ) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Select-Object -Unique

    foreach ($sdk in $sdkCandidates) {
        $buildToolsRoot = Join-Path $sdk 'build-tools'
        if (-not (Test-Path -LiteralPath $buildToolsRoot -PathType Container)) {
            continue
        }
        $candidate = Get-ChildItem -LiteralPath $buildToolsRoot -Directory |
            Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'apksigner.bat') } |
            Sort-Object {
                $number = $_.Name -replace '-.*$', ''
                try { [version]$number } catch { [version]'0.0' }
            } -Descending |
            Select-Object -First 1
        if ($null -ne $candidate) {
            return Join-Path $candidate.FullName 'apksigner.bat'
        }
    }
    throw 'Android apksigner.bat was not found under the SDK build-tools directories.'
}

function Assert-ReleaseApk {
    param(
        [string]$ApkSigner,
        [string]$WantedVersion,
        [string]$PreviousVersionTag
    )

    if (-not (Test-Path -LiteralPath $metadataFile -PathType Leaf)) {
        throw "Release metadata is missing: $metadataFile"
    }
    $metadata = Get-Content -LiteralPath $metadataFile -Raw | ConvertFrom-Json
    if ($metadata.applicationId -cne $expectedPackage -or
        $metadata.variantName -cne 'release' -or
        $metadata.elements.Count -ne 1) {
        throw 'The APK metadata does not describe the expected TyrePing release variant.'
    }
    $element = $metadata.elements[0]
    if ($element.versionName -cne $WantedVersion) {
        throw "APK version is '$($element.versionName)', expected '$WantedVersion'."
    }
    $code = [long]$element.versionCode
    if ($code -le 0) {
        throw "Invalid APK versionCode: $code"
    }
    $filename = [string]$element.outputFile
    if ($filename -ne [System.IO.Path]::GetFileName($filename)) {
        throw 'APK metadata contains an unexpected output filename/path.'
    }
    $apk = Join-Path $apkDirectory $filename
    if (-not (Test-Path -LiteralPath $apk -PathType Leaf)) {
        throw "Release APK is missing: $apk"
    }

    # Verify the cryptographic signer; an APK signed with the debug key is NOT safe
    # to publish even if Gradle reports BUILD SUCCESSFUL.
    # Suppress Java's harmless native-access warnings, but check the tool exit code.
    $oldPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $outputLines = & $ApkSigner verify --verbose --print-certs $apk 2>&1
        $signerExitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $oldPreference
    }
    $output = $outputLines | Out-String
    if ($signerExitCode -ne 0 -or $output -notmatch '(?m)^Verifies\s*$') {
        throw "APK signature verification failed:`n$output"
    }
    if ($output -notmatch 'Verified using v2 scheme \(APK Signature Scheme v2\): true' -or
        $output -notmatch 'Number of signers: 1') {
        throw 'Expected one signer and a valid APK v2 signature.'
    }
    $certificateMatch = [regex]::Match(
        $output,
        'Signer #1 certificate SHA-256 digest:\s*([a-fA-F0-9]{64})'
    )
    if (-not $certificateMatch.Success) {
        throw 'Could not extract the APK signing certificate fingerprint.'
    }
    $actualFingerprint = $certificateMatch.Groups[1].Value.ToLowerInvariant()
    if ($actualFingerprint -cne $expectedCertificateSha256) {
        throw "Wrong APK signing certificate: $actualFingerprint. Expected: $expectedCertificateSha256"
    }

    # If a previous release APK is available locally, also check monotonic
    # versionCode against the actual old package (not a guessed Gradle formula).
    if ($PreviousVersionTag) {
        $previous = $PreviousVersionTag.Substring(1)
        $oldApk = Join-Path (Join-Path $OutputRoot $previous) "TyrePing-v$previous.apk"
        if (Test-Path -LiteralPath $oldApk -PathType Leaf) {
            $aapt = Join-Path (Split-Path $ApkSigner -Parent) 'aapt.exe'
            if (Test-Path -LiteralPath $aapt -PathType Leaf) {
                $badging = (& $aapt dump badging $oldApk 2>&1 | Out-String)
                if ($LASTEXITCODE -eq 0 -and
                    $badging -match "package: name='[^']+' versionCode='(\d+)'" ) {
                    $previousCode = [long]$Matches[1]
                    if ($code -le $previousCode) {
                        throw "versionCode $code must be greater than $previousCode ($PreviousVersionTag)."
                    }
                }
            }
        }
    }

    Write-Host "Verified: $expectedPackage  v$WantedVersion  versionCode=$code" -ForegroundColor Green
    Write-Host "Signer SHA-256: $actualFingerprint" -ForegroundColor Green
    return $apk
}

function Get-PreviousReleaseTag {
    param([version]$Before)
    $valid = @(
        & git tag --merged HEAD --list 'v*' |
            Where-Object { $_ -match '^v\d+\.\d+\.\d+$' } |
            Where-Object { [version]($_.Substring(1)) -lt $Before } |
            Sort-Object { [version]($_.Substring(1)) } -Descending
    )
    Assert-LastExit 'List Git tags'
    if ($valid.Count -gt 0) { return [string]$valid[0] }
    return $null
}

function Get-ReleaseNotesContent {
    param([string]$PreviousTag)

    if (-not [string]::IsNullOrWhiteSpace($NotesFile)) {
        if (-not (Test-Path -LiteralPath $NotesFile -PathType Leaf)) {
            throw "Release notes file not found: $NotesFile"
        }
        $customNotes = Get-Content -LiteralPath $NotesFile -Raw -Encoding UTF8
        if ([string]::IsNullOrWhiteSpace($customNotes)) {
            throw 'Release notes file must not be empty.'
        }
        return $customNotes
    }

    if ($PreviousTag) {
        $subjects = @(& git log --no-merges --format='- %s' "$PreviousTag..HEAD")
        Assert-LastExit 'Read changes since previous release'
        $heading = "## Changes since $PreviousTag"
    }
    else {
        $subjects = @('- Initial TyrePing release')
        $heading = '## Changes'
    }
    if ($subjects.Count -eq 0) {
        $subjects = @('- Maintenance and release changes')
    }
    $bullets = ($subjects -join "`r`n")
    return @"
# TyrePing $tag

$heading

$bullets

## Installation

Download ``TyrePing-v$Version.apk`` and install it on Android.
Existing signed TyrePing installations should update in place.

## Verification

The release includes ``SHA256SUMS.txt`` to verify the APK download.

## Testing

Please verify the updated features on a physical device. This release has passed
unit tests and signing verification but may not yet have been tested on-device.
"@
}

$originalBytes = $null
$versionWasModified = $false
$versionWasCommitted = $false

Push-Location $repoRoot
try {
    Write-Host "TyrePing release: $tag" -ForegroundColor Cyan
    Write-Host "Repository: $repoRoot"
    Write-Host "Output: $releaseDir"
    Write-Host "Mode: $(if ($Stable) { 'stable' } else { 'pre-release' })"

    foreach ($command in @('git', 'gh', 'java')) {
        if (-not (Get-Command $command -ErrorAction SilentlyContinue)) {
            throw "Required command not found: $command"
        }
    }
    foreach ($path in @($gradlew, $versionFile, $keystore, $keysFile)) {
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
            throw "Required file missing: $path"
        }
    }
    $apkSigner = Get-ApkSigner

    $actualRoot = (& git rev-parse --show-toplevel).Trim()
    Assert-LastExit 'Check repository root'
    $normalizedActualRoot = [System.IO.Path]::GetFullPath($actualRoot.Replace('/', '\')).TrimEnd([char[]]@('\', '/'))
    $normalizedRepoRoot = $repoRoot.TrimEnd([char[]]@('\', '/'))
    if ($normalizedActualRoot -ine $normalizedRepoRoot) {
        throw 'This script must live in the root of the TyrePing Git checkout.'
    }
    $currentBranch = (& git branch --show-current).Trim()
    Assert-LastExit 'Check current branch'
    if ($currentBranch -cne $branch) {
        throw "Switch to '$branch' first (current branch: '$currentBranch')."
    }
    $changes = @(& git status --porcelain=v1 --untracked-files=normal)
    Assert-LastExit 'Check working tree'
    if ($changes.Count -gt 0) {
        throw "Working tree is not clean. Commit or remove changes before releasing:`n$($changes -join "`n")"
    }
    $remoteUrl = (& git remote get-url origin).Trim()
    Assert-LastExit 'Check origin'
    if ($remoteUrl -notmatch '(?i)^(?:https://github\.com/|git@github\.com:|ssh://git@github\.com/)TerminalAddict/TyrePing(?:\.git)?/?$') {
        throw "The 'origin' remote is unexpected: $remoteUrl"
    }

    Invoke-NativeChecked 'Fetch latest origin refs' 'git' @('fetch', 'origin')
    $head = (& git rev-parse HEAD).Trim()
    Assert-LastExit 'Read local HEAD'
    $remoteHead = (& git rev-parse 'refs/remotes/origin/tyreping').Trim()
    Assert-LastExit 'Read origin/tyreping'
    if ($head -cne $remoteHead) {
        throw 'Local tyreping does not exactly match origin/tyreping. Pull/resolve first.'
    }
    & gh auth status --hostname github.com *> $null
    Assert-LastExit 'Check GitHub CLI login'
    $repoName = (& gh repo view $repository --json nameWithOwner --jq '.nameWithOwner').Trim()
    Assert-LastExit 'Check GitHub repository access'
    if ($repoName -cne $repository) {
        throw "GitHub CLI returned an unexpected repository: $repoName"
    }

    $releaseTags = @(& gh release list --repo $repository --limit 1000 --json tagName --jq '.[].tagName')
    Assert-LastExit 'List existing GitHub releases'
    if ($releaseTags -contains $tag) {
        throw "GitHub release $tag already exists. Refusing to overwrite it."
    }

    $tagRef = "refs/tags/$tag"
    & git show-ref --verify --quiet $tagRef
    $localTagExists = ($LASTEXITCODE -eq 0)
    $remoteTagText = (& git ls-remote --tags origin $tagRef | Out-String)
    Assert-LastExit 'Check remote release tag'
    $remoteTagExists = -not [string]::IsNullOrWhiteSpace($remoteTagText)

    $oldText = [System.IO.File]::ReadAllText($versionFile)
    $versionPattern = '(?m)^(?<prefix>[ \t]*app[ \t]*=[ \t]*")(?<old>\d+\.\d+\.\d+)(?<suffix>"[ \t]*\r?)$'
    $versionMatches = [regex]::Matches($oldText, $versionPattern)
    if ($versionMatches.Count -ne 1) {
        throw "Could not find exactly one 'app = `"x.y.z`"' entry in libs.versions.toml."
    }
    $currentVersion = $versionMatches[0].Groups['old'].Value
    $requested = [version]$Version
    $previousTag = Get-PreviousReleaseTag -Before $requested
    $notesContent = Get-ReleaseNotesContent -PreviousTag $previousTag

    if ($Resume) {
        if ($currentVersion -cne $Version) {
            throw "-Resume requires the TOML version to already be $Version (found $currentVersion)."
        }
        if (-not ($localTagExists -and $remoteTagExists)) {
            throw '-Resume requires the release tag to exist locally and on origin.'
        }
        $tagCommit = (& git rev-list -n 1 $tag).Trim()
        Assert-LastExit 'Check release tag commit'
        if ($tagCommit -cne $head) {
            throw "-Resume requires $tag to point at the current tyreping HEAD."
        }
    }
    else {
        if ([version]$currentVersion -ge $requested) {
            throw "New version $Version must be greater than the current version $currentVersion."
        }
        if ($localTagExists -or $remoteTagExists) {
            throw "Tag $tag already exists. Use -Resume only after a failed GitHub publication."
        }
    }

    Write-Host "`nCurrent version: $currentVersion"
    Write-Host "Target version:  $Version"
    Write-Host "Previous tag:    $(if ($previousTag) { $previousTag } else { '(none)' })"
    if ($DryRun) {
        Write-Host '`nDry run completed. No source files, tags, or releases were changed.' -ForegroundColor Green
        return
    }

    if (-not $Yes) {
        $typed = Read-Host "Type RELEASE $tag to build, tag, push, and publish"
        if ($typed -cne "RELEASE $tag") {
            throw 'Cancelled without changes.'
        }
    }

    if (-not $Resume) {
        $originalBytes = [System.IO.File]::ReadAllBytes($versionFile)
        $vm = $versionMatches[0]
        $replacement = $vm.Groups['prefix'].Value + $Version + $vm.Groups['suffix'].Value
        $updatedText = $oldText.Substring(0, $vm.Index) + $replacement +
            $oldText.Substring($vm.Index + $vm.Length)
        [System.IO.File]::WriteAllText(
            $versionFile, $updatedText, [System.Text.UTF8Encoding]::new($false)
        )
        $versionWasModified = $true
    }

    Write-Host "`n==> Run unit tests and build the signed release" -ForegroundColor Cyan
    & $gradlew ':feature:main:testDebugUnitTest' ':feature:qrcode:testDebugUnitTest' ':app:phone:assembleRelease' `
        '--no-configuration-cache' '--no-build-cache' '--no-scan' '--console=plain'
    Assert-LastExit 'Gradle unit tests and release build'
    $null = Assert-ReleaseApk -ApkSigner $apkSigner -WantedVersion $Version -PreviousVersionTag $previousTag

    if (-not $Resume) {
        $pending = @(& git status --porcelain=v1 --untracked-files=normal)
        Assert-LastExit 'Check build side effects'
        if ($pending.Count -ne 1 -or $pending[0] -notmatch '^ M gradle/libs\.versions\.toml$') {
            throw "Unexpected changes after building. Refusing to tag:`n$($pending -join "`n")"
        }
        Invoke-NativeChecked 'Stage version change' 'git' @('add', '--', 'gradle/libs.versions.toml')
        Invoke-NativeChecked 'Check staged formatting' 'git' @('diff', '--cached', '--check')
        Invoke-NativeChecked 'Commit version change' 'git' @('commit', '-m', "Release TyrePing $tag")
        $versionWasCommitted = $true

        # Rebuild after committing: the tagged commit is the exact source for the APK.
        Write-Host "`n==> Rebuild APK from the committed source" -ForegroundColor Cyan
        & $gradlew ':app:phone:assembleRelease' '--no-configuration-cache' '--no-build-cache' '--no-scan' '--console=plain'
        Assert-LastExit 'Gradle release rebuild'
        $null = Assert-ReleaseApk -ApkSigner $apkSigner -WantedVersion $Version -PreviousVersionTag $previousTag
    }

    $metadata = Get-Content -LiteralPath $metadataFile -Raw | ConvertFrom-Json
    $sourceApk = Join-Path $apkDirectory ([string]$metadata.elements[0].outputFile)
    New-Item -ItemType Directory -Force -Path $releaseDir | Out-Null
    Copy-Item -LiteralPath $sourceApk -Destination $releaseApk -Force
    $hash = (Get-FileHash -LiteralPath $releaseApk -Algorithm SHA256).Hash.ToLowerInvariant()
    [System.IO.File]::WriteAllText(
        $checksumFile,
        "$hash  TyrePing-v$Version.apk`r`n",
        [System.Text.ASCIIEncoding]::new()
    )
    [System.IO.File]::WriteAllText(
        $generatedNotesFile,
        $notesContent.TrimEnd() + "`r`n",
        [System.Text.UTF8Encoding]::new($false)
    )
    Write-Host "`nAPK: $releaseApk" -ForegroundColor Green
    Write-Host "SHA-256: $hash" -ForegroundColor Green

    if (-not $Resume) {
        Invoke-NativeChecked 'Create annotated release tag' 'git' @('tag', '-a', $tag, '-m', "TyrePing $tag")
        Invoke-NativeChecked 'Push tyreping branch' 'git' @('push', 'origin', 'tyreping')
        Invoke-NativeChecked 'Push release tag' 'git' @('push', 'origin', $tag)
    }

    Write-Host "`n==> Publish $tag on GitHub" -ForegroundColor Cyan
    $ghArgs = @(
        'release', 'create', $tag,
        $releaseApk, $checksumFile,
        '--repo', $repository,
        '--title', "TyrePing $tag",
        '--notes-file', $generatedNotesFile,
        '--verify-tag'
    )
    if (-not $Stable) { $ghArgs += '--prerelease' }
    & gh @ghArgs
    Assert-LastExit 'Publish GitHub release'

    Invoke-NativeChecked 'Verify published release' 'gh' @('release', 'view', $tag, '--repo', $repository)
    Write-Host "`nPublished: https://github.com/$repository/releases/tag/$tag" -ForegroundColor Green
}
catch {
    if ($versionWasModified -and -not $versionWasCommitted -and $null -ne $originalBytes) {
        # Restore both the working file and staging area. Preflight guaranteed clean.
        & git restore --staged -- 'gradle/libs.versions.toml' 2>$null
        [System.IO.File]::WriteAllBytes($versionFile, $originalBytes)
        Write-Warning 'Restored libs.versions.toml because no release commit was made.'
    }
    if ($versionWasCommitted) {
        Write-Warning 'The release version has been committed. If publication failed after pushing the tag, rerun with -Resume.'
    }
    throw
}
finally {
    Pop-Location
}
