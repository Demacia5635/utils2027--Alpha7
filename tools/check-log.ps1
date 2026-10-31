# Checks the newest robot log (or the .wpilog given as the first argument).
# Run from the project root:  .\tools\check-log.ps1
$wpilib = "C:\Users\Public\wpilib\2027_alpha7"
$java = "$wpilib\jdk\bin\java.exe"
$cp = "$wpilib\maven\org\wpilib\datalog\datalog-java\2027.0.0-alpha-7\datalog-java-2027.0.0-alpha-7.jar;" +
      "$wpilib\maven\org\wpilib\wpiutil\wpiutil-java\2027.0.0-alpha-7\wpiutil-java-2027.0.0-alpha-7.jar"
& $java -cp $cp "$PSScriptRoot\CheckLog.java" @args
exit $LASTEXITCODE
