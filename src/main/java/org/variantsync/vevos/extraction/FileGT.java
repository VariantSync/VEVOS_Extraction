package org.variantsync.vevos.extraction;

import org.variantsync.diffdetective.util.Assert;

import java.io.Serializable;
import java.util.*;

public class FileGT implements Iterable<LineAnnotation>, Serializable {
    private final ArrayList<LineAnnotation> annotations;
    protected final String file;
    // We can only use the before mapping until its being mutated
    protected boolean consumed;

    protected FileGT(String file) {
        this.annotations = new ArrayList<>();
        this.consumed = false;
        this.file = file;
    }

    protected FileGT(FileGT other) {
        this.annotations = other.annotations;
        this.consumed = false;
        this.file = other.file;
    }

    protected int size() {
        return annotations.size();
    }

    protected LineAnnotation get(int index) {
        return this.annotations.get(index);
    }

    protected LineAnnotation insert(int index, LineAnnotation annotation) {
        growIfRequired(index);
        return this.annotations.set(index, annotation);
    }

    public void growIfRequired(int index) {
        // Increase the size of the array if necessary
        if (index >= this.annotations.size()) {
            this.annotations.ensureCapacity(index + 1);
        }
        for (int i = this.annotations.size(); i <= index; i++) {
            // Initialized lines get the root annotation by default
            this.annotations.add(LineAnnotation.rootAnnotation(i+1));
        }
    }

    @Override
    public Iterator<LineAnnotation> iterator() {
        return this.annotations.iterator();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("File: ").append(this.file).append(System.lineSeparator());

        for (LineAnnotation line : this) {
            sb.append(line);
            sb.append("\n");
        }
        sb.append("+++");
        sb.append("\n");
        return sb.toString();
    }

    public static class Mutable extends FileGT {

        public Mutable(String file) {
            super(file);
        }

        public Mutable insert(LineAnnotation line) {
            Assert.assertTrue(!consumed);
            this.insert(line.index(), line);
            return this;
        }

        public Complete finishMutation() {
            this.consumed = true;
            return new Complete(this);
        }
    }

    public static class Complete extends FileGT {

        private final ArrayList<BlockAnnotation> aggregatedBlocks;
        private final String csvText;

        private Complete(String fileName) {
            super(fileName);
            aggregatedBlocks = new ArrayList<>();
            csvText = "";
        }

        private Complete(Mutable incomplete) {
            super(incomplete);
            aggregatedBlocks = aggregateBlocks(this);
            csvText = csvLines(this);
        }

        public static Complete empty() {
            return new Complete("");
        }

        public String csvLines() {
            return this.csvText;
        }

        public ArrayList<BlockAnnotation> aggregatedBlocks() {
            return this.aggregatedBlocks;
        }

        private static String csvLines(Complete complete) {
            StringBuilder sb = new StringBuilder();
            for (BlockAnnotation block : complete.aggregatedBlocks) {
                sb.append(complete.file);
                sb.append(";1;");
                sb.append(block.asCSVLine());
                sb.append(System.lineSeparator());
            }
            return sb.toString();
        }

        private static ArrayList<BlockAnnotation> aggregateBlocks(Complete complete) {
            ArrayList<BlockAnnotation> blocks = new ArrayList<>();
            // The root annotation is always true and covers all lines
            BlockAnnotation rootBlock = new BlockAnnotation(1, complete.size(), "True", "True");

            LinkedList<BlockAnnotation> blockStack = new LinkedList<>();
            blockStack.push(rootBlock);
            for (LineAnnotation line : complete) {
                Assert.assertTrue(!line.equals(LineAnnotation.EMPTY), "Encountered unexpected `empty` annotation. The entire file should have been mapped");

                if (blockStack.isEmpty()) {
                    // Push a new block onto the stack
                    blockStack.push(new BlockAnnotation(line.lineNumber(), line.lineNumber(), line.featureMapping(), line.presenceCondition()));
                    continue;
                }

                BlockAnnotation lastBlock = blockStack.peekFirst();

                // Still in the same block
                if (line.featureMapping().equals(lastBlock.featureMapping()) && line.presenceCondition().equals(lastBlock.presenceCondition())) {
                    // We are still in the same block if the feature mapping remains the same
                    continue;
                }

                // new block, we have to unwind the stack in reverse order to find all completed blocks
                for(BlockAnnotation block = blockStack.peekFirst(); block != null; block=blockStack.peekFirst()) {
                    if (line.presenceCondition().contains(block.featureMapping())) {
                        // The current line is nested in retrieved block
                        break;
                    }
                    // Collect a completed block
                    block.setLineEndInclusive(line.lineNumber()-1);
                    blocks.add(blockStack.pop());
                }

                // If the current line is in a new block
                Assert.assertTrue(blockStack.peekFirst() != null, "%s\nProblem while processing line %d of file %s".formatted(complete.toString(), line.lineNumber(), complete.file));
                if (!line.featureMapping().equals(Objects.requireNonNull(blockStack.peekFirst()).featureMapping())) {
                    // Push a new block onto the stack
                    blockStack.push(new BlockAnnotation(line.lineNumber(), line.lineNumber(), line.featureMapping(), line.presenceCondition()));
                }
            }
            // Unwind the stack fully
            while(!blockStack.isEmpty()) {
                BlockAnnotation block = blockStack.pop();
                block.setLineEndInclusive(complete.size());
                blocks.add(block);
            }
            blocks.sort(Comparator.comparingInt(BlockAnnotation::lineStartInclusive));
            return blocks;
        }

    }

    public static class Removed extends FileGT {

        public Removed(String file) {
            super(file);
        }
    }
}
