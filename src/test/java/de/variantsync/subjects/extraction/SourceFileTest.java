/*package de.hub.mse.variantssync.subjects;

import org.junit.jupiter.api.Test;

import java.util.stream.Collectors;

public class SourceFileTest {
    private final static String simpleText = """
            # import something;
            
            public class Foo {
                int x;
                int y;
            }
            // End of test
            """;

    private final SourceFile simpleSourceFile = initSourceFile();

    public SourceFile initSourceFile() {
        var lines = simpleText.lines().collect(Collectors.toList());
        var sourceFile = new SourceFile(lines.size());
        for (String line : lines) {
            sourceFile.addLine(line + "\r\n");
        }
        return sourceFile;
    }

    @Test
    public void retrieveFirstLine() {
        var startLocation = new SourceFile.Location(0,0);
        var endLocation = new SourceFile.Location(0, 21);
        var result = simpleSourceFile.getContent(startLocation, endLocation);
        switch (result.state()) {
            case OK -> {
                String retrievedLine = result.unwrap();
                assert retrievedLine.equals("# import something;\r\n");
            }
            case ERR -> throw new RuntimeException(result.err().getMessage());
        }
    }

    @Test
    public void retrieveMiddleLine() {
        var startLocation = new SourceFile.Location(3,0);
        var endLocation = new SourceFile.Location(3, 12);
        var result = simpleSourceFile.getContent(startLocation, endLocation);
        switch (result.state()) {
            case OK -> {
                String retrievedLine = result.unwrap();
                assert retrievedLine.equals("    int x;\r\n");
            }
            case ERR -> throw new RuntimeException(result.err().getMessage());
        }
    }

    @Test
    public void retrieveLastLine() {
        var startLocation = new SourceFile.Location(6,0);
        var endLocation = new SourceFile.Location(6, 16);
        var result = simpleSourceFile.getContent(startLocation, endLocation);
        switch (result.state()) {
            case OK -> {
                String retrievedLine = result.unwrap();
                assert retrievedLine.equals("// End of test\r\n");
            }
            case ERR -> throw new RuntimeException(result.err().getMessage());
        }
    }

    @Test
    public void retrieveFirstWordOfFirstLine() {
        var startLocation = new SourceFile.Location(0,0);
        var endLocation = new SourceFile.Location(0, 1);
        var result = simpleSourceFile.getContent(startLocation, endLocation);
        switch (result.state()) {
            case OK -> {
                String retrievedLine = result.unwrap();
                assert retrievedLine.equals("#");
            }
            case ERR -> throw new RuntimeException(result.err().getMessage());
        }
    }

    @Test
    public void retrieveFirstWordOfMiddleLine() {
        var startLocation = new SourceFile.Location(2,0);
        var endLocation = new SourceFile.Location(2, 6);
        var result = simpleSourceFile.getContent(startLocation, endLocation);
        switch (result.state()) {
            case OK -> {
                String retrievedLine = result.unwrap();
                assert retrievedLine.equals("public");
            }
            case ERR -> throw new RuntimeException(result.err().getMessage());
        }
    }

    @Test
    public void retrieveFirstWordOfLastLine() {
        var startLocation = new SourceFile.Location(6,0);
        var endLocation = new SourceFile.Location(6, 2);
        var result = simpleSourceFile.getContent(startLocation, endLocation);
        switch (result.state()) {
            case OK -> {
                String retrievedLine = result.unwrap();
                assert retrievedLine.equals("//");
            }
            case ERR -> throw new RuntimeException(result.err().getMessage()); 
        }
    }

    @Test
    public void retrieveLastWordOfFirstLine() {
        String retrievedLine = "";
        assert retrievedLine.equals("something;\r\n");
    }

    @Test
    public void retrieveLastWordOfMiddleLine() {
        String retrievedLine = "";
        assert retrievedLine.equals("x;\r\n");
    }

    @Test
    public void retrieveLastWordOfLastLine() {
        String retrievedLine = "";
        assert retrievedLine.equals("test\r\n");
    }

    @Test
    public void retrieveMiddleWordOfFirstLine() {
        String retrievedLine = "";
        assert retrievedLine.equals("import");
    }

    @Test
    public void retrieveMiddleWordOfMiddleLine() {
        String retrievedLine = "";
        assert retrievedLine.equals("class");
    }

    @Test
    public void retrieveMiddleWordOfLastLine() {
        String retrievedLine = "";
        assert retrievedLine.equals("of");
    }

    @Test
    public void retrieveFirstTwoLines() {
        String retrievedLine = "";
        assert retrievedLine.equals("# import something;\r\n\r\n");
    }

    @Test
    public void retrieveMiddleTwoLines() {
        String retrievedLine = "";
        assert retrievedLine.equals("    int x;\r\n    int y;\r\n");
    }

    @Test
    public void retrieveLastTwoLines() {
        String retrievedLine = "";
        assert retrievedLine.equals("}\r\n// End of test\r\n");
    }

    @Test
    public void retrieveTwoLinesPartially() {
        assert false;
    }

    @Test
    public void retrieveMultipleLines() {
        assert false;
    }

    @Test
    public void retrieveMultipleLinesPartially() {
        assert false;
    }

    @Test
    public void invalidStartLine() {
        assert false;
    }

    @Test
    public void invalidEndLine() {
        assert false;
    }

    @Test
    public void invalidStartColumn() {
        assert false;
    }

    @Test
    public void invalidEndColumn() {
        assert false;
    }

    @Test
    public void invalidStartToEnd() {
        assert false;
    }
}
*/