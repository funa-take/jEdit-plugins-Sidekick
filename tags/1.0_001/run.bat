@echo off
setlocal

call setEnv.bat

java -jar "%jedit_home%\jedit.jar" -settings="%jedit_home%\settings"