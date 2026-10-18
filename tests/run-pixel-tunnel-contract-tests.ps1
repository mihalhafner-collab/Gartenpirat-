$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskJdk = Join-Path $taskRoot 'android-toolchain/jdk/jdk-17.0.20.1+1/bin'
$taskJson = Join-Path $taskRoot '.dependencies/pixel-tunnel-tests/json-20240303.jar'
$taskAndroid = Join-Path $taskRoot 'android-toolchain/sdk/platforms/android-35/android.jar'
$taskClasses = Join-Path $taskRoot 'outputs/pixel-tunnel-test-classes'
if (-not (Test-Path -LiteralPath $taskJson)) { throw 'Die isolierte JSON-Testbibliothek fehlt.' }
$taskHasher = [System.Security.Cryptography.SHA256]::Create()
try { $taskJsonHash = [BitConverter]::ToString($taskHasher.ComputeHash([System.IO.File]::ReadAllBytes($taskJson))).Replace('-', '') }
finally { $taskHasher.Dispose() }
if ($taskJsonHash -ne '3CF6CD6892E32E2B4C1C39E0F52F5248A2F5B37646FDFBB79A66B46B618414ED') { throw 'JSON-Testbibliothek stimmt nicht mit dem geprüften Hash überein.' }
New-Item -ItemType Directory -Force -Path $taskClasses | Out-Null
$taskSource = Join-Path $taskRoot 'android/app/src/main/java/at/hausgarten/service'
$taskClasspath = "$taskJson;$taskAndroid"
& (Join-Path $taskJdk 'javac.exe') -encoding UTF-8 -cp $taskClasspath -d $taskClasses (Join-Path $taskSource 'BillingRules.java') (Join-Path $taskSource 'WorklogRules.java') (Join-Path $taskSource 'PixelMcpWriteTools.java') (Join-Path $taskSource 'EntityProtectionRules.java') (Join-Path $taskSource 'PixelMcpEntityWriteTools.java') (Join-Path $taskSource 'PixelMcpReadTools.java') (Join-Path $taskSource 'PixelMcpSessionTools.java') (Join-Path $taskSource 'PixelMcpBusinessTools.java') (Join-Path $taskSource 'WorklogRelationRules.java') (Join-Path $taskSource 'DriveJournalRules.java') (Join-Path $taskSource 'OrderReviewRules.java') (Join-Path $taskSource 'ManualChangeRules.java') (Join-Path $taskSource 'McpSchema.java') (Join-Path $taskSource 'McpFileTransfer.java') (Join-Path $taskSource 'InvoiceDraftRules.java') (Join-Path $taskSource 'PixelMcpOwnerTools.java') (Join-Path $taskSource 'OwnerJournalRules.java')
if ($LASTEXITCODE -ne 0) { throw 'Neue MCP-Adapter konnten nicht übersetzt werden.' }
$taskClasspath = "$taskClasses;$taskClasspath"
& (Join-Path $taskJdk 'javac.exe') -encoding UTF-8 -cp $taskClasspath -d $taskClasses (Join-Path $PSScriptRoot 'java/at/hausgarten/service/BillingTests.java')
if ($LASTEXITCODE -ne 0) { throw 'Abrechnungsrelationstests konnten nicht übersetzt werden.' }
& (Join-Path $taskJdk 'java.exe') -cp $taskClasspath at.hausgarten.service.BillingTests | Tee-Object -FilePath (Join-Path $taskRoot 'outputs/Billing-Java-Tests.txt')
if ($LASTEXITCODE -ne 0) { throw 'Abrechnungsrelationstests fehlgeschlagen.' }
& (Join-Path $taskJdk 'javac.exe') -encoding UTF-8 -cp $taskClasspath -d $taskClasses (Join-Path $taskSource 'PixelTunnelConfig.java') (Join-Path $taskSource 'WorklogRules.java') (Join-Path $taskSource 'PixelMcpWriteTools.java') (Join-Path $taskSource 'EntityProtectionRules.java') (Join-Path $taskSource 'PixelMcpEntityWriteTools.java') (Join-Path $taskSource 'PixelMcpReadTools.java') (Join-Path $taskSource 'PixelTunnelWire.java') (Join-Path $PSScriptRoot 'java/at/hausgarten/service/PixelTunnelContractTests.java') (Join-Path $PSScriptRoot 'java/at/hausgarten/service/PixelMcpWriteTests.java') (Join-Path $PSScriptRoot 'java/at/hausgarten/service/PixelMcpEntityWriteTests.java') (Join-Path $taskSource 'WorklogRelationRules.java') (Join-Path $PSScriptRoot 'java/at/hausgarten/service/WorklogRelationTests.java')
if ($LASTEXITCODE -ne 0) { throw 'Native Tunnel-Tests konnten nicht übersetzt werden.' }
& (Join-Path $taskJdk 'java.exe') -cp "$taskClasses;$taskClasspath" at.hausgarten.service.PixelTunnelContractTests | Tee-Object -FilePath (Join-Path $taskRoot 'outputs/Pixel-Tunnel-Tests.txt')
if ($LASTEXITCODE -ne 0) { throw 'Native Tunnel-Tests fehlgeschlagen.' }
& (Join-Path $taskJdk 'java.exe') -cp "$taskClasses;$taskClasspath" at.hausgarten.service.PixelMcpWriteTests | Tee-Object -FilePath (Join-Path $taskRoot 'outputs/Pixel-MCP-Schreibtests.txt')
if ($LASTEXITCODE -ne 0) { throw 'Pixel-Schreibtests fehlgeschlagen.' }
& (Join-Path $taskJdk 'java.exe') -cp "$taskClasses;$taskClasspath" at.hausgarten.service.PixelMcpEntityWriteTests | Tee-Object -FilePath (Join-Path $taskRoot 'outputs/Pixel-MCP-Entity-Schreibtests.txt')
if ($LASTEXITCODE -ne 0) { throw 'Neue Betriebsdaten-Schreibtests fehlgeschlagen.' }
& (Join-Path $taskJdk 'java.exe') -cp "$taskClasses;$taskClasspath" at.hausgarten.service.WorklogRelationTests | Tee-Object -FilePath (Join-Path $taskRoot 'outputs/Worklog-Relation-Tests.txt')
if ($LASTEXITCODE -ne 0) { throw 'Arbeitsaufzeichnungs-Relationstests fehlgeschlagen.' }
& (Join-Path $taskJdk 'javac.exe') -encoding UTF-8 -cp $taskClasspath -d $taskClasses (Join-Path $PSScriptRoot 'java/at/hausgarten/service/PixelMcpDailyTests.java')
if ($LASTEXITCODE -ne 0) { throw 'Neue tägliche MCP-Tests konnten nicht übersetzt werden.' }
& (Join-Path $taskJdk 'java.exe') -cp "$taskClasses;$taskClasspath" at.hausgarten.service.PixelMcpDailyTests | Tee-Object -FilePath (Join-Path $taskRoot 'outputs/Pixel-MCP-Alltag-Tests.txt')
if ($LASTEXITCODE -ne 0) { throw 'Neue tägliche MCP-Tests fehlgeschlagen.' }
& (Join-Path $taskJdk 'javac.exe') -encoding UTF-8 -cp $taskClasspath -d $taskClasses (Join-Path $taskSource 'WorkSessionPauseRules.java') (Join-Path $PSScriptRoot 'java/at/hausgarten/service/WorkSessionPauseTests.java')
if ($LASTEXITCODE -ne 0) { throw 'Timer-Pausenregeln konnten nicht übersetzt werden.' }
& (Join-Path $taskJdk 'java.exe') -cp "$taskClasses;$taskClasspath" at.hausgarten.service.WorkSessionPauseTests | Tee-Object -FilePath (Join-Path $taskRoot 'outputs/Timer-Pausen-v1.37-Tests.txt')
if ($LASTEXITCODE -ne 0) { throw 'Timer-Pausenregeln fehlgeschlagen.' }
& (Join-Path $taskJdk 'javac.exe') -encoding UTF-8 -cp $taskClasspath -d $taskClasses (Join-Path $taskSource 'EntityProtectionRules.java') (Join-Path $PSScriptRoot 'java/at/hausgarten/service/EntityProtectionTests.java')
if ($LASTEXITCODE -ne 0) { throw 'Neue Schutzregeln konnten nicht übersetzt werden.' }
& (Join-Path $taskJdk 'java.exe') -cp "$taskClasses;$taskClasspath" at.hausgarten.service.EntityProtectionTests | Tee-Object -FilePath (Join-Path $taskRoot 'outputs/Entity-Schutz-v1.38-Tests.txt')
if ($LASTEXITCODE -ne 0) { throw 'Neue Schutzregeln fehlgeschlagen.' }
& (Join-Path $taskJdk 'javac.exe') -encoding UTF-8 -cp $taskClasspath -d $taskClasses (Join-Path $taskSource 'DriveJournalRules.java') (Join-Path $PSScriptRoot 'java/at/hausgarten/service/DriveJournalTests.java')
if ($LASTEXITCODE -ne 0) { throw 'Drive-Journalregeln konnten nicht übersetzt werden.' }
& (Join-Path $taskJdk 'java.exe') -cp "$taskClasses;$taskClasspath" at.hausgarten.service.DriveJournalTests | Tee-Object -FilePath (Join-Path $taskRoot 'outputs/Drive-Journal-Tests.txt')
if ($LASTEXITCODE -ne 0) { throw 'Drive-Journalregeln fehlgeschlagen.' }

& (Join-Path $taskJdk 'javac.exe') -encoding UTF-8 -cp $taskClasspath -d $taskClasses (Join-Path $taskSource 'OrderReviewRules.java') (Join-Path $PSScriptRoot 'java/at/hausgarten/service/OrderReviewTests.java')
if ($LASTEXITCODE -ne 0) { throw 'Auftragsprüfungen konnten nicht übersetzt werden.' }
& (Join-Path $taskJdk 'java.exe') -cp $taskClasspath at.hausgarten.service.OrderReviewTests | Tee-Object -FilePath (Join-Path $taskRoot 'outputs/Auftrag-Pruefen-Tests.txt')
if ($LASTEXITCODE -ne 0) { throw 'Auftragsprüfungen fehlgeschlagen.' }

foreach ($taskSuite in @('ManualChangeTests','OwnerToolsTests')) {
    & (Join-Path $taskJdk 'javac.exe') -encoding UTF-8 -cp $taskClasspath -d $taskClasses (Join-Path $PSScriptRoot "java/at/hausgarten/service/$taskSuite.java")
    if ($LASTEXITCODE -ne 0) { throw 'Owner contract tests did not compile.' }
    & (Join-Path $taskJdk 'java.exe') -cp $taskClasspath "at.hausgarten.service.$taskSuite"
    if ($LASTEXITCODE -ne 0) { throw 'Owner contract tests failed.' }
}
