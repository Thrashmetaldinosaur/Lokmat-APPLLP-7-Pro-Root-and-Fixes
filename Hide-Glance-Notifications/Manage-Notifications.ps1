param(
  [Parameter(Mandatory)][string]$Serial,
  [ValidateSet('Check','HideOverlay','RestoreOverlay','SnoozeGlance')][string]$Action='Check',
  [ValidatePattern('^[A-Za-z0-9_]+(\.[A-Za-z0-9_]+)+$')][string]$PackageName='com.appllp7.glanceauto',
  [string]$Adb='adb',
  [string]$JdkHome=$env:JAVA_HOME,
  [string]$AndroidSdk=$env:ANDROID_HOME
)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
function Invoke-Adb([string[]]$CommandArgs) {
  $output=& $Adb -s $Serial @CommandArgs 2>&1
  if($LASTEXITCODE -ne 0) {throw "ADB command failed: $($CommandArgs[0]) $output"}
  return ($output|Out-String).Trim()
}
if((Invoke-Adb -CommandArgs @('get-state')) -ne 'device'){throw 'Watch not connected and authorized'}
$device=Invoke-Adb -CommandArgs @('shell','getprop','ro.product.device')
$firmware=Invoke-Adb -CommandArgs @('shell','getprop','ro.build.display.id')
if($device -ne 'WP_C30_VP69_OLED' -or $firmware -ne 'LOKMAT_APPLLP_7_PRO_C30_EN_V1.0_20260921') {
  throw "Not the verified watch firmware; refusing notification modification"
}
if($Action -eq 'SnoozeGlance'){
  if($PackageName -ne 'com.appllp7.glanceauto'){throw 'SnoozeGlance is scoped exclusively to Glance Auto'}
  $entries=(Invoke-Adb -CommandArgs @('shell','cmd','notification','list')) -split '\r?\n'
  $key=@($entries|Where-Object {$_ -match '^0\|com\.appllp7\.glanceauto\|7432\|'})
  if($key.Count -eq 0) {Write-Output 'Glance notification not active (possibly already snoozed)';return}
  if($key.Count -ne 1){throw 'Ambiguous notification key'}
  $command="cmd notification snooze --for 31536000000 '$($key[0])'"
  Write-Output (Invoke-Adb -CommandArgs @('shell',$command))
  return
}
if(!$JdkHome -or !$AndroidSdk){throw 'Set JAVA_HOME and ANDROID_HOME for the Java helper'}
$jarTool=Join-Path $JdkHome 'bin/javac.exe'
$d8=Join-Path $AndroidSdk 'build-tools/35.0.0/d8.bat'
$androidJar=Join-Path $AndroidSdk 'platforms/android-35/android.jar'
foreach($p in @($jarTool,$d8,$androidJar)){if(!(Test-Path -LiteralPath $p)){throw "Required build tool missing: $p"}}
$source=Join-Path $PSScriptRoot 'OverlayChannelControl.java'
$tmp=Join-Path ([System.IO.Path]::GetTempPath()) ('overlay-channels-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $tmp -Force|Out-Null
$deviceFile='/data/local/tmp/overlay_channel_control.jar'
try {
  $classes=Join-Path $tmp 'classes';$dex=Join-Path $tmp 'dex'
  New-Item -ItemType Directory -Path $classes,$dex -Force|Out-Null
  & $jarTool -source 8 -target 8 -cp $androidJar -d $classes $source
  if($LASTEXITCODE -ne 0){throw 'Java compile failed'}
  & $d8 --min-api 28 --output $dex (Join-Path $classes 'OverlayChannelControl.class')
  if($LASTEXITCODE -ne 0){throw 'Dex compilation failed'}
  $zip=Join-Path $tmp 'control.zip'
  Compress-Archive -LiteralPath (Join-Path $dex 'classes.dex') -DestinationPath $zip
  Invoke-Adb -CommandArgs @('push',$zip,$deviceFile) | Out-Null
  $mode=switch($Action){'Check'{'read'}'HideOverlay'{'hide'}'RestoreOverlay'{'restore'}}
  $command="CLASSPATH=$deviceFile app_process /system/bin OverlayChannelControl $PackageName $mode"
  $result=Invoke-Adb -CommandArgs @('shell','su','-c',$command)
  Write-Output $result
} finally {
  try {Invoke-Adb -CommandArgs @('shell','rm','-f',$deviceFile)|Out-Null}catch{}
  Remove-Item -LiteralPath $tmp -Force -Recurse -ErrorAction SilentlyContinue
}


