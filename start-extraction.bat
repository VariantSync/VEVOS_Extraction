@echo "Starting extraction"

@echo "Starting the extraction"
@docker run --rm -v "%cd%/extraction-results":"/home/user/extraction-results/output" extraction %*