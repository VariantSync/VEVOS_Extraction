package org.variantsync.vevos.extraction;

import org.tinylog.Logger;
import org.variantsync.diffdetective.analysis.Analysis;
import org.variantsync.diffdetective.datasets.Repository;
import org.variantsync.vevos.extraction.analysis.FastVariabilityAnalysis;

import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import static org.variantsync.vevos.extraction.ConfigProperties.*;

/**
 * A very fast ground truth extraction that only extracts the ground truths of changed files for
 * each commit. This extraction is very useful for studies that are only interested in the evolution
 * of a software family.
 */
public class FastGroundTruthExtraction extends GroundTruthExtraction {

    public FastGroundTruthExtraction(Properties properties) {
        super(properties);
        Logger.info(
                "Starting a fast ground truth extraction that only extracts a ground truth for the changed files of each commit.");
    }

    protected BiConsumer<Repository, Path> extractionRunner() {
        return (repo, repoOutputDir) -> {
            Path extractionDir = Path.of(this.properties.getProperty(GT_SAVE_DIR));
            Path resultsRoot = extractionDir.resolve(repo.getRepositoryName());
            boolean printEnabled = Boolean.parseBoolean(this.properties.getProperty(PRINT_ENABLED));

            FastVariabilityAnalysis analysis = new FastVariabilityAnalysis(printEnabled,
                    resultsRoot, Boolean.parseBoolean(properties.getProperty(IGNORE_PC_CHANGES)),
                    Boolean.parseBoolean(properties.getProperty(EXTRACT_CODE_MATCHING)));
            final BiFunction<Repository, Path, Analysis> AnalysisFactory =
                    (r, out) -> new Analysis("PCAnalysis", List.of(analysis), r, out);

            Analysis.forEachCommit(() -> AnalysisFactory.apply(repo, repoOutputDir),
                    diffDetectiveBatchSize(), numProcessors());

            FastVariabilityAnalysis.numProcessed = 0;
        };
    }
}
