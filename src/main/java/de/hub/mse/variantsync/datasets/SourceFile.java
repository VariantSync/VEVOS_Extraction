package de.hub.mse.variantsync.datasets;

/*
class SourceFile {
    private final ArrayList<String> lines;

    SourceFile() {
        this(0);
    }

    SourceFile(int numberOfLines) {
        this.lines = new ArrayList<>(numberOfLines);
    }

    public void addLine(String line) {
        this.lines.add(line);
    }

    public Result<String> getContent(Location startLocation, Location endLocation) {
        boolean invalidStartLine = startLocation.line < 0 || startLocation.line >= this.lines.size();
        boolean invalidEndLine = endLocation.line < 0 || endLocation.line >= this.lines.size() || endLocation.line < startLocation.line;
        if (invalidStartLine || invalidEndLine) {
            return new Result<>(new IllegalArgumentException("Specification of start/end lines not valid."));
        }

        StringBuilder resultBuilder = new StringBuilder();
        for (int i = startLocation.line; i <= endLocation.line; i++) {
            String line = this.lines.get(i);
            // Consider the start column for the first line
            if (i == startLocation.line) {
                // if the end location is in the same line, we have to handle it here
                if (i == endLocation.line) {
                    // Validate that the column range is correct
                    boolean invalidStartColumn = startLocation.column < 0 || startLocation.column >= line.length();
                    boolean invalidEndColumn = endLocation.column < 0 || endLocation.column > line.length() || endLocation.column <= startLocation.column;
                    if (invalidStartColumn || invalidEndColumn) {
                        return new Result<>(new IllegalArgumentException("Specification of start/end column not valid."));
                    }
                    // This is the all there is to retrieve so we can return here
                    return new Result<>(resultBuilder.append(line, startLocation.column, endLocation.column).toString());
                } else {
                    // We have to consider more lines, so we append till the end of the line and continue with the iteration
                    resultBuilder.append(line.substring(startLocation.column));
                }
            } else if (i < endLocation.line) {
                // The entire current line has to be added
                resultBuilder.append(line);
            } else if (i == endLocation.line) {
                // This is the last line so we add everything till the end column
                resultBuilder.append(line, 0, endLocation.column);
            } else {
                throw new IllegalStateException("We should never end up here.");
            }
        }
        return new Result<>(resultBuilder.toString());
    }

    public static class Location {
        private final int line;
        private final int column;

        public Location(int line, int column) {
            this.line = line;
            this.column = column;
        }

        public int getLine() {
            return line;
        }

        public int getColumn() {
            return column;
        }
    }

}*/
