package packing;

import java.util.ArrayList;
import java.util.List;

/**
 * Fit boxes into container, i.e. perform bin packing to a single container. <br>
 * <br>
 * This attempts a brute force approach, which is very demanding in terms of resources. For use in scenarios with 'few'
 * boxes, where the complexity of a 'few' can be measured for a specific set of boxes and containers using
 * {@linkplain PermutationRotationIterator#countPermutations()} *
 * {@linkplain PermutationRotationIterator#countRotations()}. <br>
 * <br>
 * Thread-safe implementation.
 */

public class Packager {

    protected final Dimension container;

    /**
     * Logical packager for wrapping preprocessing / optimizations.
     *
     */

    public interface Adapter {
        Container pack(List<Box> boxes, Dimension dimension, long deadline);
    }

    public Packager(Dimension container ) {
        this.container = container;
    }

    protected Container pack(List<Placement> placements, Dimension dimension, PermutationRotationIterator.PermutationRotation[] rotations, long deadline) {

        PermutationRotationIterator rotator = new PermutationRotationIterator(dimension, rotations);

        return pack(placements, dimension, rotator, deadline);
    }

    public Dimension filterContainer(List<Box> boxes) {
        long volume = 0;
        for (Box box : boxes) {
            volume += box.getVolume();
        }

        if (container.getVolume() < volume || !canHold(container, boxes)) {
            return null;
        }

        return container;
    }

    /**
     *
     * Return a container which holds all the boxes in the argument
     *
     * @param boxes
     *            list of boxes to fit in a container
     * @param deadline
     *            the system time in millis at which the search should be aborted
     * @return index of container if match, -1 if not
     */

    public Container pack(List<Box> boxes, long deadline) {
        return pack(boxes, filterContainer(boxes), deadline);
    }

    public Container pack(List<Placement> placements, Dimension container, PermutationRotationIterator rotator, long deadline) {

        Container holder = new Container(container);

        // iterator over all permutations
        do {
            if (System.currentTimeMillis() > deadline) {
                break;
            }
            // iterator over all rotations

            fit: do {
                Dimension remainingSpace = container;

                int index = 0;
                while (index < rotator.length()) {
                    if (System.currentTimeMillis() > deadline) {
                        // fit2d below might have returned due to deadline
                        return null;
                    }

                    if (!rotator.isWithinHeight(index, remainingSpace.getHeight())) {
                        // clean up
                        holder.clear();

                        continue fit;
                    }

                    Box box = rotator.get(index);

                    Placement placement = placements.get(index);
                    Space levelSpace = placement.getSpace();
                    levelSpace.width = container.getWidth();
                    levelSpace.depth = container.getDepth();
                    levelSpace.height = box.getHeight();

                    placement.setBox(box);

                    levelSpace.x = 0;
                    levelSpace.y = 0;
                    levelSpace.z = holder.getStackHeight();

                    levelSpace.setParent(null);
                    levelSpace.getRemainder().setParent(null);

                    holder.addLevel();

                    index++;

                    index = fit2D(rotator, index, placements, holder, placement, deadline);

                    // update remaining space
                    remainingSpace = holder.getFreeSpace();
                }

                return holder;
            } while (rotator.nextRotation());
        } while (rotator.nextPermutation());

        return null;
    }

    protected int fit2D(PermutationRotationIterator rotator, int index, List<Placement> placements, Container holder, Placement usedSpace, long deadline) {
        // add used space box now
        // there is up to possible 2 free spaces
        holder.add(usedSpace);

        if (index >= rotator.length()) {
            return index;
        }

        if (System.currentTimeMillis() > deadline) {
            return index;
        }

        Box nextBox = rotator.get(index);
        Placement nextPlacement = placements.get(index);

        nextPlacement.setBox(nextBox);

        if (!isFreespace(usedSpace.getSpace(), usedSpace.getBox(), nextPlacement)) {
            // no additional boxes
            // just make sure the used space fits in the free space
            return index;
        }

        index++;
        // the correct space dimensions is copied into the next placement

        // attempt to fit in the remaining (usually smaller) space first

        // stack in the 'sibling' space - the space left over between the used box and the selected free space
        if (index < rotator.length()) {
            Space remainder = nextPlacement.getSpace().getRemainder();
            if (!remainder.isEmpty()) {
                Box box = rotator.get(index);

                if (box.fitsInside3D(remainder)) {
                    Placement placement = placements.get(index);
                    placement.setBox(box);

                    index++;

                    placement.getSpace().copyFrom(remainder);
                    placement.getSpace().setParent(remainder);
                    placement.getSpace().getRemainder().setParent(remainder);

                    index = fit2D(rotator, index, placements, holder, placement, deadline);
                }
            }
        }

        // fit the next box in the selected free space
        return fit2D(rotator, index, placements, holder, nextPlacement, deadline);
    }

    protected boolean isFreespace(Space freespace, Box used, Placement target) {

        // Two free spaces, on each rotation of the used space.
        // Height is always the same, used box is assumed within free space height.
        // First:
        // ........................ ........................ .............
        // . . . . . .
        // . . . . . .
        // . A . . A . . .
        // . . . . . .
        // . B . . . . B .
        // ............ . ........................ . .
        // . . . . .
        // . . . . .
        // ........................ .............
        //
        // So there is always a 'big' and a 'small' leftover area (the small is not shown).
        if (freespace.getWidth() >= used.getWidth() && freespace.getDepth() >= used.getDepth()) {

            // if B is empty, then it is sufficient to work with A and the other way around
            int b = (freespace.getWidth() - used.getWidth()) * freespace.getDepth();
            int a = freespace.getWidth() * (freespace.getDepth() - used.getDepth());

            // pick the one with largest footprint.
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

    private boolean a(Space freespace, Box used, Placement target) {
        if (target.getBox().fitsInside3D(freespace.getWidth(), freespace.getDepth() - used.getDepth(), freespace.getHeight())) {
            target.getSpace().copyFrom(freespace.getWidth(), freespace.getDepth() - used.getDepth(), freespace.getHeight(), freespace.getX(),
                    freespace.getY() + used.depth, freespace.getHeight());
            target.getSpace().getRemainder().copyFrom(freespace.getWidth() - used.getWidth(), used.getDepth(), freespace.getHeight(),
                    freespace.getX() + used.getWidth(), freespace.getY(), freespace.getZ());
            target.getSpace().setParent(freespace);
            target.getSpace().getRemainder().setParent(freespace);

            return true;
        }
        return false;
    }

    private boolean b(Space freespace, Box used, Placement target) {
        if (target.getBox().fitsInside3D(freespace.getWidth() - used.getWidth(), freespace.getDepth(), freespace.getHeight())) {
            // we have a winner
            target.getSpace().copyFrom(freespace.getWidth() - used.getWidth(), freespace.getDepth(), freespace.getHeight(), freespace.getX() + used.getWidth(),
                    freespace.getY(), freespace.getZ());

            target.getSpace().getRemainder().copyFrom(used.getWidth(), freespace.getDepth() - used.getDepth(), freespace.getHeight(), freespace.getX(),
                    freespace.getY() + used.getDepth(), freespace.getZ());

            target.getSpace().setParent(freespace);
            target.getSpace().getRemainder().setParent(freespace);
            return true;
        }
        return false;
    }

    protected Adapter adapter(List<Box> boxes) {
        // instead of placing boxes, work with placements
        // this very much reduces the number of objects created
        // performance gain is something like 25% over the box-centric approach

        final PermutationRotationIterator.PermutationRotation[] rotations = PermutationRotationIterator.toRotationMatrix(boxes);
        final List<Placement> placements = getPlacements(rotations.length);

        return new Adapter() {
            @Override
            public Container pack(List<Box> boxes, Dimension dimension, long deadline) {
                return Packager.this.pack(placements, dimension, rotations, deadline);
            }
        };
    }

    protected boolean canHold(Dimension containerBox, List<Box> boxes) {
        for (Box box : boxes) {
            if (!containerBox.canHold2D(box)) {
                return false;
            }
        }
        return true;
    }

    public static List<Placement> getPlacements(int size) {
        // each box will at most have a single placement with a space (and its remainder).
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

    public Container pack(List<Box> boxes, Dimension dimension, long deadline) {
        if (dimension == null) {
            return null;
        }

        Adapter pack = adapter(boxes);

        Container result = pack.pack(boxes, dimension, deadline);
        if (result != null) {
            return result;
        }

        return null;
    }

}
