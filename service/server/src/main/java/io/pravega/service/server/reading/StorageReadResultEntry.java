/**
 *
 *  Copyright (c) 2017 Dell Inc., or its subsidiaries.
 *
 */
package io.pravega.service.server.reading;

import io.pravega.service.contracts.ReadResultEntryContents;
import io.pravega.service.contracts.ReadResultEntryType;
import com.google.common.base.Preconditions;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Read Result Entry for data that is not readily available in memory, but exists in Storage
 */
class StorageReadResultEntry extends ReadResultEntryBase {
    private final ContentFetcher contentFetcher;
    private final AtomicBoolean contentRequested;

    /**
     * Creates a new instance of the StorageReadResultEntry class.
     *
     * @param streamSegmentOffset The offset in the StreamSegment that this entry starts at.
     * @param requestedReadLength The maximum number of bytes requested for read.
     */
    StorageReadResultEntry(long streamSegmentOffset, int requestedReadLength, ContentFetcher contentFetcher) {
        super(ReadResultEntryType.Storage, streamSegmentOffset, requestedReadLength);
        Preconditions.checkNotNull(contentFetcher, "contentFetcher");
        this.contentFetcher = contentFetcher;
        this.contentRequested = new AtomicBoolean(false);
    }

    @Override
    public void requestContent(Duration timeout) {
        Preconditions.checkState(!this.contentRequested.getAndSet(true), "Content has already been successful requested. Cannot re-request.");
        try {
            this.contentFetcher.accept(getStreamSegmentOffset(), getRequestedReadLength(), this::complete, this::fail, timeout);
        } catch (Throwable ex) {
            // Unable to request content; so reset.
            this.contentRequested.set(false);
            throw ex;
        }
    }

    @FunctionalInterface
    interface ContentFetcher {
        void accept(long streamSegmentOffset, int requestedReadLength, Consumer<ReadResultEntryContents> successCallback, Consumer<Throwable> failureCallback, Duration timeout);
    }
}