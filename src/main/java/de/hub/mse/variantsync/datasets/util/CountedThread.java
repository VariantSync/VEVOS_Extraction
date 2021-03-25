package de.hub.mse.variantsync.datasets.util;

public class CountedThread extends Thread {
    private static int numberOfInstances = 0;
    private final int instanceNumber;

    public CountedThread(Runnable r) {
        super(r);
        this.instanceNumber = numberOfInstances++;
        this.setName(String.valueOf(this.instanceNumber));
    }

    public int getInstanceNumber() {
        return this.instanceNumber;
    }
}
