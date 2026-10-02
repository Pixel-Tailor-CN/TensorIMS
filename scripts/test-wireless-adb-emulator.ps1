# TensorIMS 无线调试通知流程：由用户运行的保守验收助手。
# 不清除数据、不卸载应用、不修改 ADB 设置、不重置模拟器、不保存配对码。
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$ApkPath,
    [string]$Serial,
    [ValidateSet('tensor', 'legacy')][string]$Flavor = 'tensor',
    [string]$AdbPath = 'adb',
    [string]$AaptPath,
    [string]$OutputDirectory = (Join-Path $PWD ('TensorIMS-wireless-check-' + (Get-Date -Format 'yyyyMMdd-HHmmss')))
)
$ErrorActionPreference = 'Stop'
$packageName = if ($Flavor -eq 'tensor') { 'app.mystery0.ims.tensor' } else { 'io.github.vvb2060.ims' }
if (-not (Test-Path -LiteralPath $ApkPath -PathType Leaf)) { throw 'APK file does not exist.' }
$ApkPath = (Resolve-Path -LiteralPath $ApkPath).Path
if (-not (Get-Command $AdbPath -ErrorAction SilentlyContinue)) {
    $sdk = @($env:ANDROID_SDK_ROOT, $env:ANDROID_HOME, (Join-Path $env:LOCALAPPDATA 'Android\Sdk')) |
        Where-Object { $_ -and (Test-Path (Join-Path $_ 'platform-tools\adb.exe')) } | Select-Object -First 1
    if (-not $sdk) { throw 'adb was not found. Pass -AdbPath with your existing Android SDK adb.exe.' }
    $AdbPath = Join-Path $sdk 'platform-tools\adb.exe'
}
# 核实传入 APK 的真实安装包名，避免选错 flavor 后误测已安装的旧应用。
if (-not $AaptPath) {
    $adbCommand = Get-Command $AdbPath
    $adbSdk = Split-Path (Split-Path $adbCommand.Source -Parent) -Parent
    $roots = @($env:ANDROID_SDK_ROOT, $env:ANDROID_HOME, $adbSdk, (Join-Path $env:LOCALAPPDATA 'Android\Sdk')) | Where-Object { $_ }
    foreach ($root in $roots) {
        $buildTools = Join-Path $root 'build-tools'
        if (Test-Path -LiteralPath $buildTools) {
            $candidate = Get-ChildItem -LiteralPath $buildTools -Directory | Sort-Object Name -Descending |
                ForEach-Object { Join-Path $_.FullName 'aapt2.exe' } | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
            if ($candidate) { $AaptPath = $candidate; break }
        }
    }
}
if (-not $AaptPath -or -not (Get-Command $AaptPath -ErrorAction SilentlyContinue)) {
    throw 'Existing SDK aapt2.exe is required to verify the APK identity. Pass -AaptPath; no tools will be downloaded.'
}
$badging = (& $AaptPath dump badging $ApkPath) -join "`n"
if ($LASTEXITCODE -ne 0 -or $badging -notmatch "package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'") {
    throw 'Could not read APK package and version with aapt2.'
}
$apkPackage = $Matches[1]; $apkVersionCode = $Matches[2]; $apkVersionName = $Matches[3]
if ($apkPackage -cne $packageName) { throw "APK package $apkPackage does not match -Flavor $Flavor ($packageName). Nothing was installed." }
function Invoke-Adb {
    param([string[]]$Arguments)
    $result = & $AdbPath -s $script:Serial @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw ($result -join "`n") }
    return $result
}
$deviceList = & $AdbPath devices
if ($LASTEXITCODE -ne 0) { throw 'Could not list existing ADB devices.' }
$devices = @($deviceList | Where-Object { $_ -match '^([^\s]+)\s+device$' } | ForEach-Object { ($_.Trim() -split '\s+')[0] })
if (-not $Serial) {
    $emulators = @($devices | Where-Object { $_ -match '^emulator-\d+$' })
    if ($emulators.Count -ne 1) { throw 'Specify -Serial. Exactly one running emulator must be selected; no emulator will be created or reset.' }
    $Serial = $emulators[0]
}
if ($Serial -notin $devices) { throw 'Selected device is missing, offline or unauthorized. Resolve it manually and retry.' }
$abi = (Invoke-Adb -Arguments @('shell', 'getprop', 'ro.product.cpu.abilist')) -join ''
$sdkVersion = [int]((Invoke-Adb -Arguments @('shell', 'getprop', 'ro.build.version.sdk')) -join '')
if ($sdkVersion -lt 33) { throw 'This app requires Android 13 (API 33) or newer.' }
if ('arm64-v8a' -notin ($abi.Trim() -split ',')) {
    throw "This emulator does not advertise arm64-v8a ($abi). TensorIMS bundles an ARM64 pairing library. Use an ARM64-compatible emulator or a real ARM64 device; x86-only tests cannot validate native pairing."
}
Write-Host "Selected: $Serial; API $sdkVersion; ABIs $abi"
Write-Host "APK: $ApkPath"
Write-Host "Flavor: $Flavor ($packageName). Existing app data will not be deleted."
$confirmation = Read-Host 'Install this test APK with adb install -r? Type INSTALL to proceed'
if ($confirmation -cne 'INSTALL') { Write-Host 'Cancelled without installation.'; return }
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$metadata = @("Serial=$Serial", "API=$sdkVersion", "ABIs=$abi", "Package=$packageName", "APKVersionCode=$apkVersionCode", "APKVersionName=$apkVersionName", "APK_SHA256=$((Get-FileHash -Algorithm SHA256 -LiteralPath $ApkPath).Hash)")
$metadata | Set-Content -LiteralPath (Join-Path $OutputDirectory 'environment.txt') -Encoding UTF8
try {
    Invoke-Adb -Arguments @('install', '-r', $ApkPath) | Write-Host
} catch {
    throw "Installation failed; nothing was uninstalled or cleared. A release/debug certificate mismatch must not be bypassed by uninstalling an app containing backups. Original error: $_"
}
$installedPackage = (Invoke-Adb -Arguments @('shell', 'dumpsys', 'package', $packageName)) -join "`n"
if ($installedPackage -notmatch 'versionCode=(\d+)' -or $Matches[1] -ne $apkVersionCode) {
    throw 'Installed version does not match the selected APK; do not treat this run as validation.'
}
# 不清空设备日志；使用设备时间限定本轮采集，避免破坏其他诊断。
$since = ((Invoke-Adb -Arguments @('shell', "date '+%m-%d %H:%M:%S.000'")) -join '').Trim()
Invoke-Adb -Arguments @('shell', 'am', 'start', '-n', "$packageName/app.mystery0.ims.tensor.ui.MainActivity") | Write-Host
Write-Host ''
Write-Host 'On the device: choose Embedded mode, open backend settings, and tap notification pairing.'
Write-Host 'Allow requested notification/local-network access. Enable Wireless debugging in system settings.'
Write-Host 'Manually choose Pair device with pairing code. KEEP THE DIALOG OPEN.'
Write-Host 'Expand the TensorIMS notification and enter the code there. No port needs to be entered.'
Write-Host 'Verify the notification advances from pairing to automatic connection and reports success.'
Write-Host 'Then test cancel, a wrong code, reopen pairing dialog, and reconnect after toggling wireless debugging.'
Write-Host 'If the emulator has no Wireless debugging option or local mDNS service, record that limitation; this is not a pairing pass.'
$null = Read-Host 'Press Enter after the manual checks to collect only selected TensorIMS diagnostic tags'
Invoke-Adb -Arguments @('logcat', '-d', '-v', 'threadtime', '-T', $since, 'EmbeddedLauncher:V', 'WirelessAdbService:V', 'AdbServiceDiscovery:V', 'TensorIMSBridge:V', '*:S') |
    Set-Content -LiteralPath (Join-Path $OutputDirectory 'wireless-diagnostics.txt') -Encoding UTF8
$uidOutput = (Invoke-Adb -Arguments @('shell', 'cmd', 'package', 'list', 'packages', '-U', $packageName)) -join "`n"
if ($uidOutput -match ('package:' + [regex]::Escape($packageName) + '\s+uid:(\d+)')) {
    $appUid = $Matches[1]
    Invoke-Adb -Arguments @('logcat', '-d', '-v', 'threadtime', '-T', $since, "--uid=$appUid", 'AndroidRuntime:E', '*:S') |
        Set-Content -LiteralPath (Join-Path $OutputDirectory 'application-crashes.txt') -Encoding UTF8
}
Invoke-Adb -Arguments @('shell', 'dumpsys', 'package', $packageName) |
    Select-String -Pattern 'versionCode=|versionName=|POST_NOTIFICATIONS|ACCESS_LOCAL_NETWORK|primaryCpuAbi=' |
    Set-Content -LiteralPath (Join-Path $OutputDirectory 'installed-package.txt') -Encoding UTF8
Write-Host "Local diagnostics saved to: $OutputDirectory"
Write-Host 'Review them before sharing. This script does not claim automated or real-device pairing success.'
