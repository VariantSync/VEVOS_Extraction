@echo "Starting extraction"
if not exist "extraction-results" mkdir extraction-results

@if "%1"=="busybox" GOTO BUSYBOX
@if "%1"=="linux" GOTO LINUX

@echo "Select a SPL to extract [ ./start-extraction.bat linux | ./start-extraction.bat busybox ]"
@GOTO AFTER

:BUSYBOX
@echo "Starting the extraction"
@docker run --rm -v "%cd%/extraction-results/busybox":"/home/user/extraction-results/output" extraction %*

@GOTO AFTER

:LINUX
@echo "Starting the extraction"
@docker run --rm -v "%cd%/extraction-results/linux":"/home/user/extraction-results/output" extraction %*

:AFTER
@pause