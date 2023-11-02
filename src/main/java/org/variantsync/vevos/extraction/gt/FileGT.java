package org.variantsync.vevos.extraction.gt;

import org.tinylog.Logger;
import org.variantsync.diffdetective.util.Assert;
import org.variantsync.diffdetective.util.LineRange;
import org.variantsync.vevos.extraction.error.MatchingException;

import java.io.Serializable;
import java.util.*;

/**
 * The ground truth for a single file at a specific commit.
 */
public class FileGT implements Iterable<LineAnnotation>, Serializable {
    // The name of the file (its relative path from the root of the repo)
    protected final String file;
    // A matching of this file's lines to counterparts associated with the same commit
    // i.e., the matching line numbers before or after the changes have been applied
    // a match is -1 if there is no counterpart
    protected final ArrayList<Integer> matching;
    // List of annotation for each line
    private final ArrayList<LineAnnotation> annotations;
    // The set of variables occurring in the annotations of this file
    private final Set<String> variables;
    // We can only use the before mapping until its being mutated
    protected boolean consumed;

    protected FileGT(String file) {
        this.annotations = new ArrayList<>();
        this.matching = new ArrayList<>();
        this.consumed = false;
        this.file = file;
        this.variables = new HashSet<>();
    }

    protected FileGT(FileGT other) {
        this.annotations = other.annotations;
        this.matching = other.matching;
        this.consumed = false;
        this.file = other.file;
        this.variables = other.variables;
    }

    /**
     * @return the number of lines in the ground truth
     */
    public int size() {
        return annotations.size();
    }

    /**
     * Return the annotation at index i which annotates the line with line number (i+1).
     *
     * @param index Index in the range of [0, annotation.size())
     * @return The annotation at the given index
     */
    public LineAnnotation get(int index) {
        return this.annotations.get(index);
    }

    public Set<String> getVariables() {
        return variables;
    }

    /**
     * Inserts the given annotation at the given index and replaces the previous annotation.
     *
     * @param index      The index for insertion
     * @param annotation The annotation that is to be inserted
     * @return The previous annotation
     */
    protected LineAnnotation insert(int index, LineAnnotation annotation) {
        // +1 to account for the endif
        growIfRequired(index + 1);
        this.variables.addAll(annotation.uniqueContainedFeatures());
        return this.annotations.set(index, annotation);
    }

    /**
     * Set the match for the given line number.
     *
     * @param lineNumber  The line number in the current version of the file
     * @param matchedLine The matching line number in the counterpart version of the file
     */
    protected void setMatching(int lineNumber, int matchedLine) throws MatchingException {
        if (this.file.equals("/dev/null")) {
            // There are no valid matches for the 'null' file
            return;
        }
        if (this.matching.get(lineNumber) != -1 && this.matching.get(lineNumber) != matchedLine) {
            // TODO: Handle this case
//            throw new MatchingException("line number mismatch for " + this.file + " -- " + lineNumber
//                    + " : (" + this.matching.get(lineNumber) + " vs. " + matchedLine + ")");
            Logger.debug("line number mismatch for " + this.file + " -- " + lineNumber
                    + " : (" + this.matching.get(lineNumber) + " vs. " + matchedLine + ")");
        }
        this.matching.set(lineNumber, matchedLine);
    }

    /**
     * Increases the size of the ground truth to the given size. All added items are filled with the root annotation (i.e., true)
     *
     * @param size The size to grow to
     */
    public void growIfRequired(int size) {
        // Increase the size of the array if necessary
        this.annotations.ensureCapacity(size);
        this.matching.ensureCapacity(size);
        for (int lineNumber = this.annotations.size() + 1; lineNumber < size + 1; lineNumber++) {
            // Initialized lines get the root annotation by default
            this.annotations.add(LineAnnotation.rootAnnotation(lineNumber));
            this.matching.add(-1);
        }
    }

    /**
     * @return A flag that states whether this ground truth has been consumed by completing it. If true, the ground truth
     * can no longer be changed.
     */
    public boolean isConsumed() {
        return consumed;
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

    /**
     * A mutable file ground truth.
     */
    public static class Mutable extends FileGT {

        /**
         * Initializes an empty file ground truth for the given file name.
         *
         * @param fileName The name of the file
         */
        public Mutable(String fileName) {
            super(fileName);
        }

        /**
         * Inserts the given line annotation and replaces the previous annotation of the line identified by the line number.
         *
         * @param line The inserted line annotation
         * @return The previous annotation
         */
        public Mutable insert(LineAnnotation line) {
            Assert.assertTrue(!consumed);
            this.insert(line.index(), line);
            return this;
        }

        /**
         * Set the matching of line numbers between current file version and counterpart file version.
         *
         * @param currentRange     The range of lines in the current file version
         * @param counterpartRange The range of lines in the counterpart file version
         */
        public void setMatching(LineRange currentRange, LineRange counterpartRange) throws MatchingException {
            Assert.assertTrue(!consumed);
            // They must span the same number of lines
            int lineNumber = currentRange.fromInclusive();
            int endNumber = currentRange.toExclusive();
            int matchedLine = counterpartRange.fromInclusive();
            int matchEnd = counterpartRange.toExclusive();

            if (matchedLine != -1) {
                // If there is a match, the matched regions must have the same size
                if (endNumber - lineNumber != matchEnd - matchedLine) {
                    throw new MatchingException("line number mismatch for file" + this.file + " -- ; " +
                            "ranges have different size " + (endNumber - lineNumber) + " : " + (matchEnd - matchedLine));
                }
                // Set the matches
                for (; lineNumber < currentRange.toExclusive(); lineNumber++, matchedLine++) {
                    this.setMatching(lineNumber, matchedLine);
                }
            }
        }

        /**
         * Finish the mutation of the ground truth and return an instance of an immutable file ground truth.
         * After finishing the mutation, insert will throw an exception if it is called.
         *
         * @return An immutable file ground truth
         */
        public Complete finishMutation() {
            this.consumed = true;
            // Set the root matching to 0
            if (!this.matching.isEmpty()) {
                this.matching.set(0, 0);
            }
            return new Complete(this);
        }

    }

    /**
     * An immutable file ground truth that stores the annotations in annotated blocks and offers methods for file export.
     */
    public static class Complete extends FileGT {
        private final ArrayList<BlockAnnotation> aggregatedBlocks;
        private final String csvPCText;
        private final String csvMatchingText;

        /**
         * Initializes an immutable file ground truth with the given mutable ground truth.
         *
         * @param mutable the mutable ground truth that is 'absorbed'
         */
        private Complete(Mutable mutable) {
            super(mutable);
            aggregatedBlocks = aggregateBlocks(this);
            csvPCText = csvPCLines(this);
            csvMatchingText = csvMatchingLines(this);
        }

        /**
         * Determines the textual representation as csv lines which can be directly used for exporting the ground truth in
         * KernelHaven format.
         *
         * @return A String with the block annotations in KernelHaven's csv format
         */
        private static String csvPCLines(Complete complete) {
            StringBuilder sb = new StringBuilder();
            for (BlockAnnotation block : complete.aggregatedBlocks) {
                sb.append(complete.file);
                sb.append(";1;");
                sb.append(block.asCSVLine());
                sb.append(System.lineSeparator());
            }
            return sb.toString();
        }

        /**
         * Determines the textual representation of the line matchings as csv lines.
         *
         * @return A String with the line matchings in csv format
         */
        private static String csvMatchingLines(Complete complete) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < complete.matching.size(); i++) {
                sb.append(complete.file);
                sb.append(";");
                sb.append(i);
                sb.append(";");
                sb.append(complete.matching.get(i));
                sb.append(System.lineSeparator());
            }
            return sb.toString();
        }

        /**
         * Determines the block annotations by aggregating all lines in the ground truth to blocks sharing the same feature
         * mapping. This is done the same way as previously done by KernelHaven.
         *
         * @return The list of block annotations for this ground truth
         */
        private static ArrayList<BlockAnnotation> aggregateBlocks(Complete complete) {
            ArrayList<BlockAnnotation> blocks = new ArrayList<>();
            // The root annotation is always true and covers all lines
            BlockAnnotation rootBlock = new BlockAnnotation(1, complete.size(), new FeatureMapping("True"), new PresenceCondition("True"));

            LinkedList<BlockAnnotation> blockStack = new LinkedList<>();
            blockStack.push(rootBlock);
            for (LineAnnotation line : complete) {
                Assert.assertTrue(!line.equals(LineAnnotation.EMPTY), "Encountered unexpected `empty` annotation. The entire file should have been mapped");

                if (blockStack.isEmpty()) {
                    // Push a new block onto the stack
//                    blockStack.push(new BlockAnnotation(line.lineNumber(), line.lineNumber(), line.featureMapping(),
//                            line.presenceCondition(), line.featureMappingAfter(), line.presenceConditionAfter()));
//                    continue;
                    throw new IllegalStateException();
                }

                // Does the next line have a different annotation than the current block?
                boolean annotationChange = !blockStack.peek().annotationEquals(line) && !blockStack.peek().equals(rootBlock);

                // new block, we have to unwind the stack in reverse order to find all completed blocks
                if (annotationChange) {
                    // The current line is nested in retrieved block
                    // Collect a completed block
                    BlockAnnotation block = blockStack.pop();
                    block.setLineEndInclusive(line.lineNumber() - 1);
                    blocks.add(block);
                }

                if (blockStack.peek() == null) {
                    continue;
                }

                // Does the next line have a different annotation than the current block?
                annotationChange = !blockStack.peek().annotationEquals(line);

                Assert.assertTrue(blockStack.peekFirst() != null, () -> "%s\nProblem while processing line %d of file %s".formatted(complete.toString(), line.lineNumber(), complete.file));
                // If the current line is in a new block
                if (annotationChange) {
                    // Push a new block onto the stack
                    blockStack.push(new BlockAnnotation(line.lineNumber(), line.lineNumber(), line.featureMapping(), line.presenceCondition()));
                }
            }
            // Unwind the stack fully
            while (!blockStack.isEmpty()) {
                BlockAnnotation block = blockStack.pop();
                block.setLineEndInclusive(complete.size());
                blocks.add(block);
            }
            blocks.sort((blockAnnotation, t1) -> {
                int c = Integer.compare(blockAnnotation.lineStartInclusive(), t1.lineStartInclusive());
                if (c != 0) {
                    return c;
                } else {
                    // If the start lines are the same, the block with the higher end line is taken first
                    return -1 * Integer.compare(blockAnnotation.lineEndExclusive(), t1.lineEndExclusive());
                }
            });
            return blocks;
        }

        /**
         * Returns the textual representation as csv lines which can be directly used for exporting the ground truth in
         * KernelHaven format.
         *
         * @return A String with the block annotations in KernelHaven's csv format
         */
        public String csvPCLines() {
            return this.csvPCText;
        }

        /**
         * Returns the textual representation of the line matchings as csv lines which can be directly used for exporting.
         *
         * @return A String with the line matchings in csv format
         */
        public String csvMatchingLines() {
            return this.csvMatchingText;
        }

        /**
         * @return The list of block annotations for this file.
         */
        public ArrayList<BlockAnnotation> aggregatedBlocks() {
            return this.aggregatedBlocks;
        }

    }

    /**
     * Represents a file ground truth that can be removed because the associated file has been deleted or renamed
     */
    public static class Removed extends FileGT {

        public Removed(String file) {
            super(file);
        }
    }
}
