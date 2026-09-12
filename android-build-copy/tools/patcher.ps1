param([string]$Path,[string]$Find,[string]$Replace)
$text=[IO.File]::ReadAllText($Path)
if ($text.Contains($Find)) {
  $text=$text.Replace($Find,$Replace)
  [IO.File]::WriteAllText($Path,$text,[System.Text.UTF8Encoding]::new($false))
  Write-Output 'ok'
} else {
  Write-Output 'not-found'
}
