# HQS Wi-Fi Navi - zalozeni soukromeho repozitare a odeslani zdrojovych souboru.
# Vyžaduje Git pro Windows a GitHub CLI (gh). Nikdy neuklada hesla nebo tokeny do projektu.
$ErrorActionPreference = 'Stop'
$repository = 'ZemanAnt/hqs-wifi-navi'
$projectDirectory = $PSScriptRoot

function Require-Tool([string]$command, [string]$download) {
    if (-not (Get-Command $command -ErrorAction SilentlyContinue)) {
        throw "Chybi program '$command'. Nainstalujte jej z $download a spustte tento skript znovu."
    }
}
function Check-Exit([string]$description) {
    if ($LASTEXITCODE -ne 0) { throw "$description selhalo (kod $LASTEXITCODE)." }
}

try {
    Set-Location -LiteralPath $projectDirectory
    Require-Tool 'git' 'https://git-scm.com/downloads/win'
    Require-Tool 'gh' 'https://cli.github.com/'
    if (-not (Test-Path -LiteralPath '.github/workflows/android-apk.yml')) {
        throw 'Chybi workflow .github/workflows/android-apk.yml. Rozbalte cely projekt.'
    }
    if (Test-Path -LiteralPath '.git') {
        throw 'Slozka uz je Git repozitar. Aby nedoslo k nechtenemu prepsani, zastavuji.'
    }

    Write-Host 'Overuji prihlaseni do GitHubu...' -ForegroundColor Cyan
    & gh auth status --hostname github.com *> $null
    if ($LASTEXITCODE -ne 0) {
        Write-Host 'Otevre se bezpecne prihlaseni do GitHubu v prohlizeci.'
        & gh auth login --hostname github.com --git-protocol https --web
        Check-Exit 'Prihlaseni do GitHubu'
    }
    $currentLogin = (& gh api user --jq .login).Trim()
    Check-Exit 'Kontrola uctu'
    if ($currentLogin -ine 'ZemanAnt') {
        throw "Prihlasen ucet '$currentLogin', ale projekt ma jit pod 'ZemanAnt'. Prepnout: gh auth switch."
    }

    & gh repo view $repository --json nameWithOwner *> $null
    if ($LASTEXITCODE -eq 0) {
        throw "Repozitar $repository jiz existuje. Skript jej kvuli bezpecnosti neprepise."
    }
    Write-Host 'Pripravuji lokalni Git repozitar...' -ForegroundColor Cyan
    & git init -b main
    Check-Exit 'git init'
    & git add --all
    Check-Exit 'git add'
    & git -c "user.name=$currentLogin" -c "user.email=$currentLogin@users.noreply.github.com" commit -m 'Initial HQS Wi-Fi Navi Android 1.2.0'
    Check-Exit 'git commit'

    Write-Host "Zakladam SOUKROMY GitHub repozitar $repository a odesilam soubory..." -ForegroundColor Cyan
    & gh repo create $repository --private --source . --remote origin --push
    Check-Exit 'Zalozeni repozitare a push'

    Write-Host ''
    Write-Host 'Repozitar zalozen a kod odeslan.' -ForegroundColor Green
    Write-Host "Projekt: https://github.com/$repository"
    Write-Host "GitHub Actions: https://github.com/$repository/actions/workflows/android-apk.yml"
    Write-Host 'APK: GitHub Actions > Android debug APK > posledni beh > Artifacts.'
    try { Start-Process "https://github.com/$repository/actions/workflows/android-apk.yml" } catch { }
} catch {
    Write-Host ''
    Write-Host "NELZE DOKONCIT: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host 'Nic dal neprovadim. Pokud byl repozitar mezitim zalozen, zkontrolujte ho na GitHubu.'
    exit 1
}
