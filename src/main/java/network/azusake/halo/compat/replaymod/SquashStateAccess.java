package network.azusake.halo.compat.replaymod;

/** Internal bridge for copying a filter's neutral trimming accumulator. */
public interface SquashStateAccess { void halo$copyReplayState(ReplayMarkerSupport.State state); }
