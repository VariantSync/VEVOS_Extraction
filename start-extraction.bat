@echo "Starting extraction"

@if "%1"=="busybox" GOTO BUSYBOX
@if "%1"=="linux" GOTO LINUX

@echo "Select a SPL to extract [ ./start-extraction.bat linux | ./start-extraction.bat busybox ]"
@GOTO AFTER

:BUSYBOX
@if not exist "extraction-results" mkdir extraction-results
@if not exist "extraction-results/busybox" mkdir extraction-results/busybox

@echo "Starting the extraction"
@docker run --rm -v "%cd%/extraction-results/busybox":"/home/user/extraction-results/output" variability-extraction %*

@GOTO AFTER

:LINUX
@if not exist "extraction-results" mkdir extraction-results
@if not exist "extraction-results/linux" mkdir extraction-results/linux

@echo "Starting the extraction"
@docker run --rm -v "%cd%/extraction-results/linux":"/home/user/extraction-results/output" variability-extraction %*

:AFTER
@pause