@echo "Stopping all running extractions. This will take a moment..."
@FOR /f "tokens=*" %%i IN ('docker ps -a -q --filter "ancestor=variability-extraction"') DO docker stop %%i
@echo "...done."
pause