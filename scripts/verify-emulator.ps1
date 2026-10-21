param([string]$Serial = 'emulator-5554')

$ErrorActionPreference = 'Stop'
if ($Serial -notmatch '^emulator-\d+$') { throw 'This script accepts emulator serials only.' }
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
$adb = Join-Path $sdk 'platform-tools/adb.exe'
$projectRoot = Split-Path -Parent $PSScriptRoot
$emulated = & $adb -s $Serial shell getprop ro.kernel.qemu
if ($LASTEXITCODE -ne 0 -or "$emulated".Trim() -ne '1') { throw 'The selected emulator is not ready.' }

Push-Location $projectRoot
try {
    & ./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Android test build failed.' }
    & $adb -s $Serial install -r app/build/outputs/apk/debug/app-debug.apk
    if ($LASTEXITCODE -ne 0) { throw 'Emulator application update failed; no uninstall is attempted.' }
    & $adb -s $Serial install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
    if ($LASTEXITCODE -ne 0) { throw 'Emulator test package update failed.' }
    & $adb -s $Serial shell pm grant io.github.asagnc.aurora android.permission.POST_NOTIFICATIONS
    if ($LASTEXITCODE -ne 0) { throw 'Emulator notification permission grant failed.' }
    $result = & $adb -s $Serial shell am instrument -w -e notAnnotation io.github.asagnc.aurora.root.RootRequired -e notClass io.github.asagnc.aurora.browser.BrowserRecoveryTest,io.github.asagnc.aurora.runtime.AgentRunRecoveryTest io.github.asagnc.aurora.test/androidx.test.runner.AndroidJUnitRunner
    $exitCode = $LASTEXITCODE
    $text = $result -join [Environment]::NewLine
    $directory = Join-Path $projectRoot 'build/reports'
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
    Set-Content -LiteralPath (Join-Path $directory 'emulator-check.txt') -Value $text -Encoding utf8
    Write-Output $text
    $count = [regex]::Match($text, 'OK \((\d+) tests?\)')
    if ($exitCode -ne 0 -or !$count.Success -or [int]$count.Groups[1].Value -lt 65 -or
        $text -match 'FAILURES|INSTRUMENTATION_FAILED|Process crashed') { throw 'Emulator verification did not produce sixty-five passing tests.' }
    $recovery = & $adb -s $Serial shell am instrument -w -e class io.github.asagnc.aurora.browser.BrowserRecoveryTest io.github.asagnc.aurora.test/androidx.test.runner.AndroidJUnitRunner
    $recoveryExit = $LASTEXITCODE
    $recoveryText = $recovery -join [Environment]::NewLine
    Add-Content -LiteralPath (Join-Path $directory 'emulator-check.txt') -Value $recoveryText -Encoding utf8
    Write-Output $recoveryText
    if ($recoveryExit -ne 0 -or $recoveryText -notmatch 'OK \(1 test\)' -or
        $recoveryText -match 'FAILURES|INSTRUMENTATION_FAILED|Process crashed') { throw 'Fresh-process browser recovery failed.' }
    foreach ($phase in @('prepare', 'verify')) {
        $method = if ($phase -eq 'prepare') { 'prepare' } else { 'verifyAfterProcessDeath' }
        $agent = & $adb -s $Serial shell am instrument -w -e class "io.github.asagnc.aurora.runtime.AgentRunRecoveryTest#$method" -e auroraRecoveryPhase $phase io.github.asagnc.aurora.test/androidx.test.runner.AndroidJUnitRunner
        $agentExit = $LASTEXITCODE
        $agentText = $agent -join [Environment]::NewLine
        Add-Content -LiteralPath (Join-Path $directory 'emulator-check.txt') -Value $agentText -Encoding utf8
        Write-Output $agentText
        if ($agentExit -ne 0 -or $agentText -notmatch 'OK \(1 test\)' -or
            $agentText -match 'FAILURES|INSTRUMENTATION_FAILED|Process crashed|AssumptionFailure') { throw "Agent process recovery $phase failed." }
        if ($phase -eq 'prepare') {
            & $adb -s $Serial shell am force-stop io.github.asagnc.aurora
            if ($LASTEXITCODE -ne 0) { throw 'Emulator process stop failed.' }
        }
    }
} finally { Pop-Location }
