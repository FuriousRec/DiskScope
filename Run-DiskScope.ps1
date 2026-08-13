Set-Location -LiteralPath $PSScriptRoot

$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot'
$mavenBin = 'C:\Program Files\JetBrains\IntelliJ IDEA 2025.2.3\plugins\maven\lib\maven3\bin'
$env:Path = "$env:JAVA_HOME\bin;$mavenBin;$env:Path"

mvn javafx:run
