@echo off
call "C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build\vcvars64.bat" >nul 2>&1
set PATH=%PATH%;C:\Program Files\nodejs;%USERPROFILE%\.cargo\bin
cd /d "c:\Users\Boy-Torres\Desktop\danbron\desktop"
echo === Cargo version:
cargo --version
echo === Node version:
node --version
echo === Building Tauri...
npx tauri build
