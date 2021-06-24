@echo "Starting extraction"

@if "%1"=="busybox" GOTO BUSYBOX
@if "%1"=="linux" GOTO LINUX

@echo "Select a SPL to extract [ ./start-extraction.bat linux | ./start-extraction.bat busybox ]"
@GOTO AFTER

:BUSYBOX
@echo Deleting old busybox data under ./extraction-results/busybox
@del /s /f /q extraction-results\busybox\*.*
@for /f %%f in ('dir /ad /b extraction-results\busybox\') do rd /s /q extraction-results\busybox\%%f

@echo "Starting the extraction"
@docker run --user "1000:1000" --name variability-extraction-busybox --mount source=busybox-extraction,target=/home/user/extraction-results/output variability-extraction %*

@echo "Copying data from the Docker container to ./extraction-results/busybox"
@md extraction-results
@cd extraction-results
@md busybox
@cd ..

@docker run --rm --volumes-from variability-extraction-busybox^
    -u "1000:1000"^
    -v "%cd%/extraction-results/busybox":"/home/user/data"^
    ubuntu cp -rf /home/user/extraction-results/output /home/user/data

@echo "Removing Docker container and volume"
@docker container rm variability-extraction-busybox
@docker volume rm busybox-extraction
@GOTO AFTER

:LINUX
@echo Delete the old linux data
@del /s /f /q extraction-results\linux\*.*
@for /f %%f in ('dir /ad /b extraction-results\linux\') do rd /s /q extraction-results\linux\%%f

@echo "Starting the extraction"
@docker run^
    --user "1000:1000"^
    --name variability-extraction-linux^
    --mount source=linux-extraction,target=/home/user/extraction-results/output^
    variability-extraction %*

@echo "Copying data from the Docker container to ./extraction-results/linux"
@md extraction-results
@cd extraction-results
@md linux
@cd ..

@docker run --rm --volumes-from variability-extraction-linux^
    -u "1000:1000"^
    -v "%cd%/extraction-results/linux":"/home/user/data"^
    ubuntu cp -rf /home/user/extraction-results/output /home/user/data

@echo "Removing Docker container and volume"
@docker container rm variability-extraction-linux
@docker volume rm linux-extraction

:AFTER
@pause