/*
 * Copyright 2018 Indiana University.
 *
 * Mark H. Wood, IUPUI University Library, Apr 13, 2018
 */

package edu.iupui.ulib.dspace;

/**
 * Holder for Bitstream count and size aggregates.
 *
 * @author Mark H. Wood <mwood@iupui.edu>
 */
public class BitstreamCounts
{
    private final long count;
    private final long totalSize;

    public BitstreamCounts(Long count, Long totalSize)
    {
        this.count = count;
        if (null == totalSize)
            this.totalSize = 0;
        else
            this.totalSize = totalSize;
    }

    /**
     * @return the count.
     */
    public long getCount()
    {
        return count;
    }

    /**
     * @return the aggregate size of all Bitstream.
     */
    public long getTotalSize()
    {
        return totalSize;
    }
}
