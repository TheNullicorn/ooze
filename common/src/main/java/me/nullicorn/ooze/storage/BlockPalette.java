package me.nullicorn.ooze.storage;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import me.nullicorn.ooze.serialize.OozeDataOutputStream;
import me.nullicorn.ooze.serialize.OozeSerializable;
import me.nullicorn.ooze.world.BlockState;
import org.jetbrains.annotations.NotNull;

/**
 * A set of {@link BlockState block states} that exist within a volume of Minecraft blocks. Each
 * state is identified by a positive integer, and no state or identifier can be used more than once
 * at any given time in the same palette.
 * <p>
 * When used in conjunction with an {@link me.nullicorn.ooze.serialize.IntArray integer array} or
 * similar structure, it provides a compact means of storing states for large volumes of blocks.
 *
 * @author Nullicorn
 */
public class BlockPalette implements OozeSerializable, Iterable<BlockState> {

  private final List<BlockState> registeredStates;

  public BlockPalette() {
    this.registeredStates = new ArrayList<>();
  }

  /**
   * @return The block state associated with that ID, or null if none exists in this palette.
   * @see #getStateId(BlockState)
   */
  public BlockState getState(int stateId) {
    return registeredStates.get(stateId);
  }

  /**
   * @return The integer used to identify the {@code state} in the palette, or {@code -1} if the
   * palette does not contain that state.
   * @see #getOrAddStateId(BlockState)
   */
  public int getStateId(BlockState state) {
    return registeredStates.indexOf(state);
  }

  /**
   * Same as {@link #getStateId(BlockState)}, but the {@code state} is automatically added if the
   * palette does not already contain it. If that happens, the returned value is the new identifier
   * created for the state.
   *
   * @return The integer used to identify the {@code state} in the palette.
   */
  public int getOrAddStateId(BlockState state) {
    int stateId = registeredStates.indexOf(state);
    if (stateId < 0) {
      registeredStates.add(state);
      stateId = registeredStates.indexOf(state);
    }
    return stateId;
  }

  /**
   * @return The number of unique block states stored in the palette.
   */
  public int size() {
    return registeredStates.size();
  }

  @Override
  public void serialize(OozeDataOutputStream out) {
    // TODO: 5/16/21 Add serialization for BlockPalette.
    throw new UnsupportedOperationException("Not yet implemented");
  }

  @NotNull
  @Override
  public Iterator<BlockState> iterator() {
    return registeredStates.iterator();
  }

  // TODO: 5/16/21 Add "PaletteUpgrader" for mapping old palette indices to updated ones.
//  public static PaletteUpgrader getLegacyUpgradeMap() {
//    // Return an upgrade map for pre-flattening data values to modern block states.
//    throw new UnsupportedOperationException("Not yet implemented.");
//  }
//
//  public PaletteUpgrader removeState(BlockState state) {
//    registeredStates.remove(state);
//  }
//
//  public PaletteUpgrader removeState(int stateId) {
//    registeredStates.remove(stateId);
//  }
//
//  public PaletteUpgrader generateUpgradeMap() {
//    throw new UnsupportedOperationException("Not yet implemented.");
//  }
}
