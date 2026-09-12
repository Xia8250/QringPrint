param(
    [Parameter(Mandatory = $true)][string]$ApkPath,
    [string]$PublicBaseUrl = "",
    [int]$VersionCode = 41,
    [string]$VersionName = "4.0",
    [string]$Title = "HuanxongKuaiyin 4.0",
    [string]$Changelog = "Initial release",
    [switch]$Force,

    [ValidateSet("r2", "github")][string]$Mode = "r2",
    [string]$GitHubUser = "",
    [string]$GitHubRepo = "",
    [string]$GitHubTag = "latest"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $ApkPath)) { throw "APK not found: $ApkPath" }

$apk = Get-Item $ApkPath
$apkName = $apk.Name
$sizeBytes = $apk.Length
$sha256 = (Get-FileHash -Path $apk.FullName -Algorithm SHA256).Hash.ToLower()

$manifestUrl = ""
$downloadUrl = ""

if ($Mode -eq "r2") {
    if ([string]::IsNullOrWhiteSpace($PublicBaseUrl)) { throw "R2 mode requires -PublicBaseUrl" }
    $base = $PublicBaseUrl.TrimEnd('/')
    $downloadUrl = "$base/$apkName"
    $manifestUrl = "$base/update.json"
} elseif ($Mode -eq "github") {
    if ([string]::IsNullOrWhiteSpace($GitHubUser)) { throw "GitHub mode requires -GitHubUser" }
    if ([string]::IsNullOrWhiteSpace($GitHubRepo)) { throw "GitHub mode requires -GitHubRepo" }
    $tag = if ($GitHubTag) { $GitHubTag } else { "latest" }
    $manifestUrl = "https://cdn.jsdelivr.net/gh/$GitHubUser/$GitHubRepo@$tag/update.json"
    $downloadUrl = "https://cdn.jsdelivr.net/gh/$GitHubUser/$GitHubRepo@$tag/$apkName"
}

$outDir = $apk.DirectoryName
$jsonPath = Join-Path $outDir "update.json"
$archivePath = Join-Path $outDir ("update." + $VersionCode + ".json")

$obj = [ordered]@{
    versionCode              = $VersionCode
    versionName              = $VersionName
    title                    = $Title
    changelog                = $Changelog
    force                    = [bool]$Force
    minSupportedVersionCode  = 0
    url                      = $downloadUrl
    sizeBytes                = $sizeBytes
    sha256                   = $sha256
}

$json = $obj | ConvertTo-Json -Depth 4
$utf8Bom = [System.Text.UTF8Encoding]::new($true)
[System.IO.File]::WriteAllText($jsonPath, $json, $utf8Bom)
[System.IO.File]::WriteAllText($archivePath, $json, $utf8Bom)

Write-Host ""
Write-Host "Generated: $jsonPath"
Write-Host "Archive:   $archivePath"
Write-Host "----------------------------------------"
Write-Host "  mode:       $Mode"
Write-Host "  versionCode: $VersionCode"
Write-Host "  versionName: $VersionName"
Write-Host "  apk:         $apkName ($([math]::Round($sizeBytes/1MB,2)) MB)"
Write-Host "  sha256:      $sha256"
Write-Host "  manifestUrl: $manifestUrl"
Write-Host "  url:         $downloadUrl"
Write-Host "  sizeBytes:   $sizeBytes"
Write-Host "----------------------------------------"
Write-Host ""
Write-Host "Next steps:"
if ($Mode -eq "r2") {
    Write-Host "  1) Upload $apkName and update.json to your R2 bucket"
    Write-Host "  2) Test $manifestUrl in browser"
    Write-Host "  3) Update UpdateConfig.kt manifestUrl -> $manifestUrl"
} else {
    Write-Host "  1) git add . && git commit && git tag $GitHubTag && git push origin main --tags"
    Write-Host "  2) Test $manifestUrl in browser"
    Write-Host "  3) Update UpdateConfig.kt manifestUrl -> $manifestUrl"
}
Write-Host "  4) Ask Codex to rebuild APK"