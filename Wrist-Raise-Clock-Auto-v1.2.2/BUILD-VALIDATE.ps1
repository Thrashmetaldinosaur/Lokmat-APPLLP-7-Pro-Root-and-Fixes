param(
 [string]$JdkHome=$env:JAVA_HOME,
 [string]$AndroidSdk=$env:ANDROID_HOME,
 [string]$Gradle='gradle'
)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
if(!$JdkHome -or !(Test-Path (Join-Path $JdkHome 'bin/javac.exe'))) {throw 'Set JAVA_HOME to JDK17'}
if(!$AndroidSdk -or !(Test-Path (Join-Path $AndroidSdk 'platforms/android-35/android.jar'))) {throw 'Set ANDROID_HOME to Android SDK35'}
$env:JAVA_HOME=$JdkHome
$env:ANDROID_HOME=$AndroidSdk
$env:ANDROID_SDK_ROOT=$AndroidSdk
$env:PATH=(Join-Path $JdkHome 'bin')+';'+$env:PATH
$classes=Join-Path ([System.IO.Path]::GetTempPath()) ('glance-test-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $classes -Force|Out-Null
try {
 $src=Join-Path $PSScriptRoot 'app/src/main/java/com/appllp7/glanceauto'
 $qa=Join-Path $PSScriptRoot 'qa'
 $sourceFiles=@('AutoPolicy.java','WakeReason.java','BootPolicy.java','BrightnessPolicy.java','MediaGuardPolicy.java') | ForEach-Object {Join-Path $src $_}
 $testFiles=@('AutoPolicyStress.java','WakeReasonStress.java','BootDecisionStress.java','BrightnessPrearmStress.java')|ForEach-Object {Join-Path $qa $_}
 & (Join-Path $JdkHome 'bin/javac.exe') -d $classes @sourceFiles @testFiles
 if($LASTEXITCODE -ne 0){throw 'Java compilation failed'}
 foreach($test in @('AutoPolicyStress','WakeReasonStress','BootDecisionStress','BrightnessPrearmStress')){
   & (Join-Path $JdkHome 'bin/java.exe') -cp $classes ('com.appllp7.glanceauto.'+$test)
   if($LASTEXITCODE -ne 0){throw "Offline test failed: $test"}
 }
 & $Gradle --offline --no-daemon --console=plain -p $PSScriptRoot :app:assembleDebug
 if($LASTEXITCODE -ne 0){throw 'Gradle build failed'}
 Write-Output 'GLANCE_STABLE_1_2_2_OFFLINE_BUILD_PASS'
} finally {Remove-Item -LiteralPath $classes -Recurse -Force -ErrorAction SilentlyContinue}
