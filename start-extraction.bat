@echo "Starting extraction"

@echo "Starting the extraction"
@docker run --rm -v "%cd%/ground-truth":"/home/user/ground-truth" extraction %*