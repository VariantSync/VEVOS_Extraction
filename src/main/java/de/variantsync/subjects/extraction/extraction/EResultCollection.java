package de.variantsync.subjects.extraction.extraction;

/***
 * <p>
 * Specifies in which way the results of several analysis tasks are collected.
 * </p>
 *
 * ## None
 * <p>
 * The results are not collected and remain in the working directory of each task
 * </p>
 *
 * ## CollectedDirectories
 * <p>
 * The results are collected in a global output directory. Here, each commit has
 * its own directory, in which the related results are stored.
 * </p>
 *
 * ## Repository
 * <p>
 * The results are collected in a global output directory in which a git repository is
 * initialized. The results for each SPL commit are committed to this repository. This way,
 * the size of the collected data can be reduced, because the result files of different commits
 * are highly similar.
 * </p>
 *
 */
public enum EResultCollection {
    NONE,
    COLLECTED_DIRECTORIES,
    LOCAL_REPOSITORY,
    REMOTE_REPOSITORY
}
