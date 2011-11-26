@echo off
setlocal
set jarfile=SideKick.jar


call setEnv.bat

dir /b /s src\*.java > list.txt

call javac -target 1.5 -source 1.5 -classpath "%cpath%" -d build @list.txt
call jar cfm %jarfile% META-INF\MANIFEST.MF -C build .

copy /y %jarfile% "%installdir%"