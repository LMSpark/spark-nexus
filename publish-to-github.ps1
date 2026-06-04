$ErrorActionPreference = "Stop"

$repo = "LMSpark/spark-nexus"
$remoteUrl = "https://github.com/$repo.git"

if (-not (Test-Path ".git")) {
  git init
}

$branch = git branch --show-current
if (-not $branch) {
  git checkout -b main
} elseif ($branch -ne "main") {
  git branch -M main
}

$origin = git remote get-url origin 2>$null
if ($LASTEXITCODE -ne 0) {
  git remote add origin $remoteUrl
} elseif ($origin -ne $remoteUrl) {
  git remote set-url origin $remoteUrl
}

gh auth status | Out-Host

$repoExists = $true
gh repo view $repo *> $null
if ($LASTEXITCODE -ne 0) {
  $repoExists = $false
}

if (-not $repoExists) {
  gh repo create $repo --private --source . --remote origin --push
} else {
  git push -u origin main
}

Write-Host "Published $repo on main."
