package com.hirewise.be.repository.projection;

/**
 * UC-42: lifetime share/click counters for one publishing channel, summed over
 * the Job Positions in the caller's filter.
 * <p>
 * {@code job_posting_channels} keeps running totals and stores no per-click
 * timestamp, so these two numbers are deliberately NOT filtered by the report's
 * date range - the UI labels the columns as lifetime figures.
 */
public interface ChannelTrafficRow {

    String getUtmSource();

    long getShareCount();

    long getClickCount();
}
