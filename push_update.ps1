param(
    [Parameter(Mandatory = $true)][string]$GitHubUser,
    [string]$GitHubRepo = "huanxongkuaiyin-updates",
    [Parameter(Mandatory = $true)][string]$ApkPath,
    [int]$VersionCode = 40,
    [string]$VersionName = "4.0",
    [string]$Title = "HuanxongKuaiyin 4.0",
    [string]$Changelog = "Initial release",
    [switch]$Force,
    [string]$Token = "",
    [string]$Branch = "main"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $ApkPath)) { throw "APK not found: $ApkPath" }

# 1) Prepare local work dir = APK's directory / _update_repo
$apk = Get-Item $ApkPath
$workDir = Join-Path $apk.DirectoryName "_update_repo"
if (-not (Test-Path $workDir)) { New-Item -ItemType Directory -Path $workDir | Out-Null }
Set-Location $workDir

# 2) Copy APK into work dir (English filename to avoid GitHub issues)
$apkNameEn = "HuanxongKuaiyin-$VersionName.apk"
Copy-Item -Path $apk.FullName -Destination (Join-Path $workDir $apkNameEn) -Force
Write-Host "[1/5] Copied APK -> $apkNameEn"

# 3) Generate update.json
&D:\QrintPrint-main\gen_update_json.ps1 `
    -ApkPath (Join-Path $workDir $apkNameEn) `
    -PublicBaseUrl "" `
    -Mode github `
    -GitHubUser $GitHubUser `
    -GitHubRepo $GitHubRepo `
    -GitHubTag "latest" `
    -VersionCode $VersionCode `
    -VersionName $VersionName `
    -Title $Title `
    -Changelog $Changelog `
    -Force:$Force | Out-Null

# Copy update.json into work dir
Copy-Item -Path (Join-Path $apk.DirectoryName "update.json") -Destination (Join-Path $workDir "update.json") -Force
Write-Host "[2/5] Generated update.json"

# 4) Init git if needed
if (-not (Test-Path (Join-Path $workDir ".git"))) {
    git init -b $Branch 2>&1 | Out-Null
    git config user.name  "$GitHubUser"
    git config user.email "$GitHubUser@users.noreply.github.com"
}
Write-Host "[3/5] Git repo ready"

# 5) Commit
git add $apkNameEn update.json 2>&1 | Out-Null
git commit -m "v$VersionName (build $VersionCode)" 2>&1 | Out-Null
Write-Host "[4/5] Committed"

# 6) Push
$remoteUrl = "https://github.com/$GitHubUser/$GitHubRepo.git"
$existingRemote = ""
try {
    $existingRemote = git remote get-url origin 2>&1
} catch {
    $existingRemote = ""
}
# Always force origin to the correct URL (clear any stale/wrong origin)
$prevEAP = $ErrorActionPreference
$ErrorActionPreference = "Continue"
git remote remove origin 2>&1 | Out-Null
$ErrorActionPreference = $prevEAP
git remote add origin $remoteUrl
Write-Host "Remote origin set to $remoteUrl"

Write-Host "[5/5] Pushing to $remoteUrl ..."
if (-not [string]::IsNullOrWhiteSpace($Token)) {
    $authUrl = "https://x-access-token:$Token@github.com/$GitHubUser/$GitHubRepo.git"
    git push -u $authUrl $Branch 2>&1
} else {
    git push -u origin $Branch 2>&1
}
if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "Push failed! Common causes:"
    Write-Host "  1) Repo not created: go to https://github.com/new and create $GitHubRepo (Public)"
    Write-Host "  2) No credentials: pass -Token <your-PAT> to bypass browser auth"
    Write-Host ""
    Write-Host "Get a PAT:"
    Write-Host "  GitHub avatar -> Settings -> Developer settings -> Personal access tokens"
    Write-Host "  -> Tokens (classic) -> Generate new token -> check 'repo' permission -> Generate"
    Write-Host "  When pushing: username = your GitHub username, password = the token you just created"
    Write-Host ""
    throw "Push failed"
}

Write-Host ""
Write-Host "All done!"
Write-Host ""
$manifestUrl = "https://cdn.jsdelivr.net/gh/$GitHubUser/$GitHubRepo@latest/update.json"
Write-Host "Verify manifestUrl:"
Write-Host "  Open $manifestUrl in browser, you should see JSON"
Write-Host ""
Write-Host "Tell Codex the manifestUrl, he will update UpdateConfig.kt and rebuild APK"