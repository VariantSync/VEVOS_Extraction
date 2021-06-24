@echo "Stopping extraction"

@if "%1"=="busybox" GOTO BUSYBOX
@if "%1"=="linux" GOTO LINUX

@echo "Select a process to stop [ ./stop-extraction.bat linux | ./stop-extraction.bat busybox ]"
@GOTO AFTER

:BUSYBOX
@echo "Stopping busybox extraction"
@docker container stop variability-extraction-busybox
@GOTO AFTER

:LINUX
@echo "Stopping linux extraction"
@docker container stop variability-extraction-linux

:AFTER
pause