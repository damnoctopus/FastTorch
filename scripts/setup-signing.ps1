param([switch]$UploadToGitHub)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$signingDirectory = Join-Path $projectRoot '.signing'
$propertiesFile = Join-Path $signingDirectory 'release.properties'
$keystoreFile = Join-Path $signingDirectory 'fasttorch-release.p12'

if (-not (Test-Path -LiteralPath $propertiesFile)) {
    if (Test-Path -LiteralPath $keystoreFile) {
        throw 'A signing key already exists without its properties file. Restore the properties from your backup.'
    }
    $keytool = (Get-Command keytool -ErrorAction Stop).Source
    New-Item -ItemType Directory -Force -Path $signingDirectory | Out-Null
    $random = [Security.Cryptography.RandomNumberGenerator]::Create()
    $bytes = New-Object byte[] 32
    $random.GetBytes($bytes)
    $random.Dispose()
    $password = [Convert]::ToBase64String($bytes)
    $env:FASTTORCH_SETUP_PASSWORD = $password
    try {
        & $keytool -genkeypair -keystore $keystoreFile -storetype PKCS12 -alias fasttorch `
            -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=FastTorch' `
            -storepass:env FASTTORCH_SETUP_PASSWORD -keypass:env FASTTORCH_SETUP_PASSWORD
        if ($LASTEXITCODE -ne 0) { throw 'Key generation failed.' }
        @(
            'storeFile=.signing/fasttorch-release.p12'
            "storePassword=$password"
            'keyAlias=fasttorch'
            "keyPassword=$password"
        ) | Set-Content -LiteralPath $propertiesFile -Encoding ascii
    } finally {
        Remove-Item Env:FASTTORCH_SETUP_PASSWORD -ErrorAction SilentlyContinue
    }
    Write-Host 'Created the release signing key. Back up the .signing directory securely.'
} else {
    Write-Host 'Using the existing signing configuration.'
}

if ($UploadToGitHub) {
    $gh = (Get-Command gh -ErrorAction Stop).Source
    $properties = @{}
    foreach ($line in Get-Content -LiteralPath $propertiesFile) {
        if ($line -match '^([^=]+)=(.*)$') { $properties[$Matches[1]] = $Matches[2] }
    }
    $configuredKeystore = Join-Path $projectRoot $properties['storeFile']
    $secrets = @{
        FASTTORCH_KEYSTORE_BASE64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($configuredKeystore))
        FASTTORCH_STORE_PASSWORD = $properties['storePassword']
        FASTTORCH_KEY_ALIAS = $properties['keyAlias']
        FASTTORCH_KEY_PASSWORD = $properties['keyPassword']
    }
    foreach ($name in $secrets.Keys) {
        $startInfo = New-Object Diagnostics.ProcessStartInfo
        $startInfo.FileName = $gh
        $startInfo.Arguments = "secret set $name --repo damnoctopus/FastTorch"
        $startInfo.UseShellExecute = $false
        $startInfo.CreateNoWindow = $true
        $startInfo.RedirectStandardInput = $true
        $process = [Diagnostics.Process]::Start($startInfo)
        try {
            $process.StandardInput.Write($secrets[$name])
            $process.StandardInput.Close()
            $process.WaitForExit()
            if ($process.ExitCode -ne 0) {
                throw "Could not upload $name. Authenticate with gh auth login and retry."
            }
        } finally {
            $process.Dispose()
        }
    }
    Write-Host 'Uploaded signing secrets to damnoctopus/FastTorch.'
}
