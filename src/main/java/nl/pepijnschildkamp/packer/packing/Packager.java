package nl.pepijnschildkamp.packer.packing;

import java.util.ArrayList;
import java.util.Arrays;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

public class Packager {

    private final Dimension wave;

    public Packager(Dimension wave) {
        this.wave = wave;
    }

    private Dimension filterWave(List<Item> items) {
        long volume = 0;
        for (Item item : items) {
            volume += item.getVolume();
        }

        if (wave.getVolume() < volume)
            throw new WaveTooSmallException(
                    "Wave (volume: " + wave.getVolume() + " is te klein voor al deze dozen (totaal volume: " + volume + ")");

        if (!canHold(wave, items))
            throw new WaveTooSmallException("Wave is te klein voor ingevoerde (enkele) dozen.");

        return wave;
    }

    public Wave pack(List<Item> items) {
        return pack(items, filterWave(items));
    }

    private Wave pack(List<Placement> placements, Dimension wave, PermutationItemIterator rotator) {
        Wave holder = new Wave(wave);

        do {
            for (Item item : rotator.next()) {
                Dimension remainingSpace = wave;

                int index = 0;
                while (index < rotator.getLength()) {
                    if (!rotator.isWithinHeight(index, remainingSpace.getHeight())) {
                        holder.clear();
                        continue;
                    }

                    Placement placement = placements.get(index);
                    Space levelSpace = placement.getSpace();
                    levelSpace.width = wave.getWidth();
                    levelSpace.depth = wave.getDepth();
                    levelSpace.height = item.getHeight();

                    placement.setItem(item);

                    levelSpace.x = 0;
                    levelSpace.y = 0;
                    levelSpace.z = holder.getStackHeight();

                    levelSpace.setParent(null);
                    levelSpace.getRemainder()
                            .setParent(null);

                    holder.addLevel();

                    index++;
                    index = fit2D(rotator, index, placements, holder, placement);

                    remainingSpace = holder.getFreeSpace();
                }

                return holder;
            }
        } while (rotator.hasNext());

        return null;
    }

    private int fit2D(PermutationItemIterator rotator, int index, List<Placement> placements, Wave holder, Placement usedSpace) {
        holder.add(usedSpace);

        if (index >= rotator.getLength()) {
            return index;
        }

        Item nextItem = rotator.get(index);
        Placement nextPlacement = placements.get(index);

        nextPlacement.setItem(nextItem);

        if (!isFreespace(usedSpace.getSpace(), usedSpace.getItem(), nextPlacement)) {
            return index;
        }

        index++;

        if (index < rotator.getLength()) {
            Space remainder = nextPlacement.getSpace()
                    .getRemainder();
            if (!remainder.isEmpty()) {
                Item item = rotator.get(index);

                if (item.fitsInside3D(remainder.getWidth(), remainder.getDepth(), remainder.getHeight())) {
                    Placement placement = placements.get(index);
                    placement.setItem(item);

                    index++;

                    placement.getSpace()
                            .copyFrom(remainder);
                    placement.getSpace()
                            .setParent(remainder);
                    placement.getSpace()
                            .getRemainder()
                            .setParent(remainder);

                    index = fit2D(rotator, index, placements, holder, placement);
                }
            }
        }

        return fit2D(rotator, index, placements, holder, nextPlacement);
    }

    private boolean isFreespace(Space freespace, Item used, Placement target) {
        if (freespace.getWidth() >= used.getWidth() && freespace.getDepth() >= used.getDepth()) {
            int b = (freespace.getWidth() - used.getWidth()) * freespace.getDepth();
            int a = freespace.getWidth() * (freespace.getDepth() - used.getDepth());

            if (b >= a) {
                if (b > 0 && b(freespace, used, target)) {
                    return true;
                }
                return a > 0 && a(freespace, used, target);
            } else {
                if (a > 0 && a(freespace, used, target)) {
                    return true;
                }
                return b > 0 && b(freespace, used, target);
            }
        }
        return false;
    }

    private boolean a(Space freespace, Item used, Placement target) {
        if (target.getItem()
                .fitsInside3D(freespace.getWidth(), freespace.getDepth() - used.getDepth(), freespace.getHeight())) {
            target.getSpace()
                    .copyFrom(freespace.getWidth(), freespace.getDepth() - used.getDepth(), freespace.getHeight(), freespace.getX(),
                            freespace.getY() + used.getDepth(), freespace.getHeight());
            target.getSpace()
                    .getRemainder()
                    .copyFrom(freespace.getWidth() - used.getWidth(), used.getDepth(), freespace.getHeight(), freespace.getX() + used.getWidth(),
                            freespace.getY(), freespace.getZ());
            target.getSpace()
                    .setParent(freespace);
            target.getSpace()
                    .getRemainder()
                    .setParent(freespace);

            return true;
        }
        return false;
    }

    private boolean b(Space freespace, Item used, Placement target) {
        if (target.getItem()
                .fitsInside3D(freespace.getWidth() - used.getWidth(), freespace.getDepth(), freespace.getHeight())) {
            target.getSpace()
                    .copyFrom(freespace.getWidth() - used.getWidth(), freespace.getDepth(), freespace.getHeight(), freespace.getX() + used.getWidth(),
                            freespace.getY(), freespace.getZ());

            target.getSpace()
                    .getRemainder()
                    .copyFrom(used.getWidth(), freespace.getDepth() - used.getDepth(), freespace.getHeight(), freespace.getX(),
                            freespace.getY() + used.getDepth(), freespace.getZ());

            target.getSpace()
                    .setParent(freespace);
            target.getSpace()
                    .getRemainder()
                    .setParent(freespace);
            return true;
        }
        return false;
    }

    private boolean canHold(Dimension waveItem, List<Item> items) {
        for (Item item : items) {
            if (!waveItem.canHold2D(item.getWidth(), item.getDepth(), item.getHeight())) {
                return false;
            }
        }
        return true;
    }

    private static List<Placement> getPlacements(int size) {
        List<Placement> placements = new ArrayList<>(size);

        for (int i = 0; i < size; i++) {
            Space a = new Space();
            Space b = new Space();
            a.setRemainder(b);
            b.setRemainder(a);

            placements.add(new Placement(a));
        }
        return placements;
    }

    private Wave pack(List<Item> items, Dimension dimension) {
        if (dimension == null) {
            return null;
        }

        PermutationItemIterator permutationIterator = new PermutationItemIterator(items);
        final List<Placement> placements = getPlacements(permutationIterator.getLength());

        Wave result = pack(placements, dimension, permutationIterator);
        if (result != null) {
            return result;
        }

        return null;
    }

    private class PermutationItemIterator implements Iterator<List<Item>> {
        private int[] keys;
        private Map<Integer, Item> objectMap;
        private boolean[] direction;
        private List<Item> nextPermutation;
        private int permutationLength = 1;

        private int getLength() {
            return permutationLength - 1;
        }

        private boolean isWithinHeight(int fromIndex, int height) {
            for (int i = (fromIndex + 1); i < getLength(); i++) {
                if (objectMap.get(i)
                        .getHeight() > height) {
                    return false;
                }
            }
            return true;
        }

        protected Item get(Integer index) {
            return objectMap.get(index);
        }

        protected PermutationItemIterator(Collection<Item> coll) {
            if (coll == null) {
                throw new NullPointerException("The collection must not be null");
            } else {
                keys = new int[coll.size()];
                direction = new boolean[coll.size()];
                Arrays.fill(direction, false);

                objectMap = new HashMap<>();

                for (Iterator it = coll.iterator(); it.hasNext(); keys[permutationLength - 1] = permutationLength++) {
                    Item e = (Item) it.next();
                    objectMap.put(permutationLength, e);
                }

                nextPermutation = new ArrayList<>(coll);
            }
        }

        @Override
        public boolean hasNext() {
            return nextPermutation != null;
        }

        @Override
        public List<Item> next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            } else {
                int indexOfLargestMobileInteger = -1;
                int largestKey = -1;

                int offset;
                for (offset = 0; offset < keys.length; ++offset) {
                    if ((direction[offset] && offset < keys.length - 1 && keys[offset] > keys[offset + 1]
                            || !direction[offset] && offset > 0 && keys[offset] > keys[offset - 1]) && keys[offset] > largestKey) {
                        largestKey = keys[offset];
                        indexOfLargestMobileInteger = offset;
                    }
                }

                if (largestKey == -1) {
                    List<Item> toReturn = nextPermutation;
                    nextPermutation = null;
                    return toReturn;
                } else {
                    offset = direction[indexOfLargestMobileInteger] ? 1 : -1;
                    int tmpKey = keys[indexOfLargestMobileInteger];
                    keys[indexOfLargestMobileInteger] = keys[indexOfLargestMobileInteger + offset];
                    keys[indexOfLargestMobileInteger + offset] = tmpKey;
                    boolean tmpDirection = direction[indexOfLargestMobileInteger];
                    direction[indexOfLargestMobileInteger] = direction[indexOfLargestMobileInteger + offset];
                    direction[indexOfLargestMobileInteger + offset] = tmpDirection;
                    List<Item> nextP = new ArrayList<>();

                    for (int i = 0; i < keys.length; ++i) {
                        if (keys[i] > largestKey) {
                            direction[i] = !direction[i];
                        }

                        nextP.add(objectMap.get(keys[i]));
                    }

                    List<Item> result = nextPermutation;
                    nextPermutation = nextP;
                    return result;
                }
            }
        }
    }
}