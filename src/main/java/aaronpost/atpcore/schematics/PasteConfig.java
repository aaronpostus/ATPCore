package aaronpost.atpcore.schematics;

/**
 * Supplies batching parameters for batched schematic pastes. Game plugins can
 * install an implementation backed by their own config so values stay
 * live-editable; the default is used otherwise.
 */
public interface PasteConfig {
    PasteConfig DEFAULT = new PasteConfig() {
        @Override
        public int batchBlocksSize() { return 200; }
        @Override
        public int ticksBetweenBatches() { return 2; }
    };

    /** Number of blocks placed per batch. */
    int batchBlocksSize();

    /** Delay in ticks between batches. */
    int ticksBetweenBatches();
}
