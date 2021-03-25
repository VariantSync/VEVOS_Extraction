package de.hub.mse.variantsync.datasets.util;

import net.ssehub.kernel_haven.util.Logger;

import java.util.concurrent.ThreadFactory;

public class CountingThreadFactory implements ThreadFactory {
    private final Logger logger;

    public CountingThreadFactory(Logger logger) {
        this.logger = logger;
    }
    @Override
    public Thread newThread(Runnable r) {
        CountedThread thread = new CountedThread(r);
        logger.logDebug("Started thread number " + thread.getInstanceNumber() + " with id " + thread.getId());
        return thread;
    }
}
