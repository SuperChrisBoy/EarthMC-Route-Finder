package net.earthmc.routefinder.iceeditor;

import java.util.EnumSet;
import java.util.Set;

public final class SnapSettings {
  // Placement must default to the block directly under the cursor. More aggressive snapping
  // (8-way line direction, nearby vertices, segments, stations, or chunks) is explicitly opt-in.
  private final EnumSet<SnapTarget> enabled = EnumSet.noneOf(SnapTarget.class);

  public boolean enabled(SnapTarget target) {
    return enabled.contains(target);
  }

  public void set(SnapTarget target, boolean value) {
    if (value) enabled.add(target);
    else enabled.remove(target);
  }

  public Set<SnapTarget> enabledTargets() {
    return Set.copyOf(enabled);
  }
}
