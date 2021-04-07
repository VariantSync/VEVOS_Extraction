package de.hub.mse.variantsync.datasets.creation;

import de.hub.mse.variantsync.datasets.util.GitUtil;
import net.ssehub.kernel_haven.util.Logger;
import org.eclipse.jgit.api.Git;

import java.io.IOException;
import java.nio.file.Path;

public class AnalysisResultCrawler {
    private static final Logger LOGGER = Logger.get();

    public static void crawl(Path pathToRepo) {
        try {
            Git git = GitUtil.initGitForRepo(pathToRepo.toFile());
        } catch (IOException e) {
            LOGGER.logException("Was not able to initialize git for repository directory " + pathToRepo, e);
        }
    }
}
