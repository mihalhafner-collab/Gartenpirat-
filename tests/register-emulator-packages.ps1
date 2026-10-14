$ErrorActionPreference = 'Stop'
$appTestRoot = Split-Path $PSScriptRoot -Parent
$sdkTestRoot = Join-Path $appTestRoot 'android-toolchain\sdk'
# The downloaded tools ZIP had an extra cmdline-tools/ nesting. Copy, never
# overwrite/remove, into the SDK's canonical latest/bin + latest/lib layout.
$toolsDirectory = Join-Path $sdkTestRoot 'cmdline-tools\latest'
foreach ($itemName in @('bin','lib','NOTICE.txt','source.properties')) {
    $target = Join-Path $toolsDirectory $itemName
    if (-not (Test-Path -LiteralPath $target)) {
        Copy-Item -LiteralPath (Join-Path $toolsDirectory ('cmdline-tools\' + $itemName)) -Destination $target -Recurse
    }
}
# Restore SDK-manager metadata after the verified official ZIP installation.
# Source nodes and license are copied unchanged from Google's repository metadata.
$definitions = @(
    @{ Path='emulator'; Cache='sdkbin-1_b75fa71f-repository2-4_xml'; Archive='emulator-windows_x64-16428233.zip'; Directory='emulator' },
    @{ Path='system-images;android-35;google_apis;x86_64'; Cache='sdkbin-1_707bcb95-sys-img2-5_xml'; Archive='x86_64-35_r09.zip'; Directory='system-images\android-35\google_apis\x86_64' }
)
foreach ($definition in $definitions) {
    $target = Join-Path $sdkTestRoot ($definition.Directory + '\package.xml')
    if (Test-Path -LiteralPath $target) { continue }
    [xml]$source = Get-Content -LiteralPath (Join-Path $appTestRoot ('.android-user\cache\' + $definition.Cache)) -Raw
    $remote = $source.SelectNodes("//*[local-name()='remotePackage' and @path='$($definition.Path)']") | Where-Object {
        $_.SelectSingleNode(".//*[local-name()='url'][text()='$($definition.Archive)']")
    } | Select-Object -First 1
    if (-not $remote) { throw 'Offizielle Paketbeschreibung fehlt.' }
    $expectedHash = $remote.SelectSingleNode(".//*[local-name()='complete'][*[local-name()='url']='$($definition.Archive)']/*[local-name()='checksum']").InnerText
    $archive = Join-Path $appTestRoot ('android-toolchain\emulator-downloads\' + $definition.Archive)
    if ((Get-FileHash -LiteralPath $archive -Algorithm SHA1).Hash -ne $expectedHash) { throw 'Registrierung ohne verifizierte Prüfsumme abgelehnt.' }
    $document = New-Object System.Xml.XmlDocument
    $root = $document.CreateElement('common','repository','http://schemas.android.com/repository/android/common/02')
    $document.AppendChild($root) | Out-Null
    foreach ($attribute in $source.DocumentElement.Attributes) {
        $root.SetAttributeNode($document.ImportNode($attribute,$true)) | Out-Null
    }
    $licenseRef = $remote.SelectSingleNode("./*[local-name()='uses-license']").GetAttribute('ref')
    $license = $source.SelectSingleNode("//*[local-name()='license' and @id='$licenseRef']")
    $root.AppendChild($document.ImportNode($license,$true)) | Out-Null
    $local = $document.CreateElement('localPackage')
    $local.SetAttribute('path',$definition.Path)
    $local.SetAttribute('obsolete','false')
    foreach ($name in @('type-details','revision','display-name','uses-license','dependencies')) {
        $node = $remote.SelectSingleNode("./*[local-name()='$name']")
        if ($node) { $local.AppendChild($document.ImportNode($node,$true)) | Out-Null }
    }
    $root.AppendChild($local) | Out-Null
    $document.Save($target)
    Write-Host "Registriert: $($definition.Path)"
}
