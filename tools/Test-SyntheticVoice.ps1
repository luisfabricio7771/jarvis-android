param([string]$Python = 'python')
$ErrorActionPreference='Stop'
Set-Location -LiteralPath (Split-Path -Parent $PSScriptRoot)
New-Item -ItemType Directory -Path '.tools\wake-fixtures' -Force | Out-Null
Add-Type -AssemblyName System.Speech
$synth=New-Object System.Speech.Synthesis.SpeechSynthesizer
try {
    $format=New-Object System.Speech.AudioFormat.SpeechAudioFormatInfo(16000,[System.Speech.AudioFormat.AudioBitsPerSample]::Sixteen,[System.Speech.AudioFormat.AudioChannel]::Mono)
    $synth.SelectVoice('Microsoft Sabina Desktop')
    $phrases=@('Jarvis','Hola Jarvis','Oye Jarvis','Hola','Buenos dias','Gracias','Donde estan mis llaves','Ya vi','Ya ves','Vamos a comenzar','Abre mis archivos')
    for ($i=0; $i -lt $phrases.Count; $i++) {
        $synth.SetOutputToWaveFile("$PWD\.tools\wake-fixtures\$i.wav",$format)
        $synth.Speak($phrases[$i]); $synth.SetOutputToNull()
    }
    $phrases | ConvertTo-Json | Set-Content -LiteralPath '.tools\wake-fixtures\phrases.json' -Encoding UTF8
} finally { $synth.Dispose() }
& $Python tools/validate_wake.py
if ($LASTEXITCODE -ne 0) { throw 'Falló la activación local sintética.' }
& $Python tools/validate_silero.py
if ($LASTEXITCODE -ne 0) { throw 'Falló la validación de Silero.' }
