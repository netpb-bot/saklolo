# One-shot install on a USB-connected phone (USB debugging on): APK + sideloaded models from .\models\.
# Usage: .\install.ps1            (one phone plugged in)
#        .\install.ps1 -s SERIAL  (several; see `adb devices`)
param([string]$s)
$ErrorActionPreference = "Stop"
$pre = if ($s) { @("-s", $s) } else { @() }
function A { & adb @pre @args; if ($LASTEXITCODE) { throw "adb $args failed" } }

A install -r "$PSScriptRoot\app\build\outputs\apk\debug\app-debug.apk"
$dst = "/sdcard/Android/data/ph.saklolo/files"
A shell am start -W -n ph.saklolo/.MainActivity  # first launch creates $dst
A shell am force-stop ph.saklolo
# Preferred: push to tmp, then run-as cp so the app owns the files (debug build only).
# Some phones block run-as from /sdcard; then push straight into the app folder.
foreach ($f in Get-ChildItem "$PSScriptRoot\models") {
    A push $f.FullName "/data/local/tmp/$($f.Name)"
    & adb @pre shell run-as ph.saklolo cp "/data/local/tmp/$($f.Name)" "$dst/"
    if ($LASTEXITCODE) { A shell mv "/data/local/tmp/$($f.Name)" "$dst/" } else { A shell rm "/data/local/tmp/$($f.Name)" }
}
A shell ls -la $dst
A shell am force-stop ph.saklolo  # next launch loads the models
"Done. Open Saklolo."
