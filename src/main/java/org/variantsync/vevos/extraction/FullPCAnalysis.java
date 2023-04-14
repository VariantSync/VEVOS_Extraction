package org.variantsync.vevos.extraction;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.revwalk.RevCommit;
import org.tinylog.Logger;
import org.variantsync.diffdetective.analysis.Analysis;
import org.variantsync.diffdetective.metadata.EditClassCount;
import org.variantsync.diffdetective.variation.diff.Time;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Extracts ground truths for all repositories in a dataset. The ground truth consists of presence conditions for each file,
 * a list of all variables, and commit metadata.
 */
public class FullPCAnalysis implements Analysis.Hooks, PCAnalysis {
    public static int numProcessed = 0;
    private final Hashtable<String, GroundTruth> groundTruthMap;
    private final Path diffDetectiveCache;

    public FullPCAnalysis(Path diffDetectiveCache) {
        this.groundTruthMap = new Hashtable<>();
        this.diffDetectiveCache = diffDetectiveCache;
    }

    @Override
    public void endCommit(Analysis analysis) throws Exception {
        RevCommit commit = analysis.getCurrentCommit();
        var repo = analysis.getRepository();
        Path resultFile = diffDetectiveCache.resolve("pc").resolve(repo.getRepositoryName()).resolve(commit.getName() + ".gt");
        Files.createDirectories(resultFile.getParent());

        GroundTruth groundTruth = this.groundTruthMap.getOrDefault(commit.getName(), new GroundTruth(new HashMap<>(), new HashSet<>()));
        // Complete all new or updated file ground truths
        PCAnalysis.makeComplete(groundTruth);
        Serde.serialize(resultFile.toFile(), groundTruth);
        this.groundTruthMap.remove(commit.getName());
        synchronized (FullPCAnalysis.class) {
            FullPCAnalysis.numProcessed++;
            if (FullPCAnalysis.numProcessed % 1_000 == 0) {
                Logger.info("Finished Commit ({}): {}", FullPCAnalysis.numProcessed, commit.name());
            }
        }
    }

    @Override
    public void initializeResults(Analysis analysis) {
        analysis.append(EditClassCount.KEY, new EditClassCount());
    }

    @Override
    public boolean analyzeDiffTree(Analysis analysis) throws Exception {
        GroundTruth groundTruth = this.groundTruthMap.computeIfAbsent(analysis.getCurrentCommit().getName(), commit -> new GroundTruth(new HashMap<>(), new HashSet<>()));
//        Show.diff(analysis.getCurrentDiffTree()).showAndAwait();
        // Get the ground truth for this file
        String fileNameBefore = analysis.getCurrentPatch().getFileName(Time.BEFORE);
        String fileNameAfter = analysis.getCurrentPatch().getFileName(Time.AFTER);
        Logger.debug("Name of processed file is %s -> %s".formatted(fileNameBefore, fileNameAfter));
        DiffEntry.ChangeType changeType = analysis.getCurrentPatch().getChangeType();
        if (changeType == DiffEntry.ChangeType.DELETE || (changeType != DiffEntry.ChangeType.ADD && !fileNameBefore.equals(fileNameAfter))) {
            // We set the entry of the old name as removed if the file was deleted or the name changed without the file being added as new
            groundTruth.fileGTs().put(fileNameBefore, new FileGT.Removed(fileNameBefore));
        }

        if (analysis.getCurrentPatch().getChangeType() == DiffEntry.ChangeType.DELETE) {
            // We return early, if the file has been completely deleted
            return true;
        }

        // At this point, it must be an instance of FileGT.Mutable
        final FileGT.Mutable fileGT = (FileGT.Mutable) groundTruth.computeIfAbsent(fileNameAfter, k -> new FileGT.Mutable(fileNameAfter));

        analysis.getCurrentDiffTree().forAll(node -> {
            Logger.debug("Node: {}", node);
            PCAnalysis.analyzeNode(fileGT, node, Time.AFTER);
        });

        return true;
    }

}
