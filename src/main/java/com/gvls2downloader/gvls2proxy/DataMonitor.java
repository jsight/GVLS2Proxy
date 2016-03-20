package com.gvls2downloader.gvls2proxy;

import java.util.*;

class DataSample {
    long byteCount;
    long start;
    long end;

    DataSample(long bc, long ts, long tf) {
        byteCount = bc;
        start = ts;
        end = tf;
    }
}

public class DataMonitor {
    protected List<DataSample> samples;
    protected long epoch;

    public DataMonitor() {
        samples = Collections.synchronizedList(new ArrayList<>());
        epoch = System.currentTimeMillis();
    }

    // Add a sample with a start and finish time.
    public void addSample(long bcount, long ts, long tf) {
        samples.add(new DataSample(bcount, ts, tf));
        if (samples.size() > 1000)
            samples.remove(0);
    }

    // Get the data rate of a given sample.
    public double getRateFor(int sidx) {
        double rate = 0.0f;
        int scnt = samples.size();
        if (scnt > sidx && sidx >= 0) {
            DataSample s = samples.get(sidx);
            long start = s.start;
            long end = s.end;
            if (sidx >= 1) {
                DataSample prev = samples.get(sidx - 1);
                start = prev.end;
            }

            long msec = end - start;
            rate = 1000 * (float) s.byteCount / (float) msec;
        }

        return rate;
    }

    // Get the rate of the last sample
    public double getLastRate() {
        int scnt = samples.size();
        return getRateFor(scnt - 1);
    }

    // Get the average rate over all samples.
    public double getAverageRate() {
        long msCount = 0;
        long byteCount = 0;
        long start = 0;
        long finish = 0;
        int scnt = samples.size();
        for (int i = 0; i < scnt; i++) {
            DataSample ds = samples.get(i);

            start = ds.start;
            if (i > 0) {
                DataSample prev = samples.get(i - 1);
                start = ds.end;
            } else
                start = epoch;

            finish = ds.end;
            if (i < scnt - 1) {
                DataSample next = samples.get(i + 1);
                finish = ds.start;
            } else
                finish = System.currentTimeMillis();

            // Only include this sample if we could figure out a start
            // and finish time for it.
            byteCount += ds.byteCount;
            msCount += finish - start;
        }

        double rate = -1;
        if (msCount > 0) {
            rate = 1000 * (double) byteCount / (double) msCount;
        }

        return rate;
    }
}