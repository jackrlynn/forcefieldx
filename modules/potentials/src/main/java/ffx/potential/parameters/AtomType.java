/**
 * Title: Force Field X
 * Description: Force Field X - Software for Molecular Biophysics.
 * Copyright: Copyright (c) Michael J. Schnieders 2001-2011
 *
 * This file is part of Force Field X.
 *
 * Force Field X is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 as published
 * by the Free Software Foundation.
 *
 * Force Field X is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Force Field X; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA
 */
package ffx.potential.parameters;

import java.util.Comparator;

/**
 * The AtomType class represents one molecular mechanics atom type.
 *
 * @author Michael J. Schnieders
 *
 * @since 1.0
 */
public final class AtomType extends BaseType implements Comparator<String> {

    /**
     * Atom type.
     */
    public int type;
    /**
     * Atom class.
     */
    public int atomClass;
    /**
     * Short name (ie CH3/CH2 etc).
     */
    public final String name;
    /**
     * Description of the atom's bonding environment.
     */
    public final String environment;
    /**
     * Atomic Number.
     */
    public final int atomicNumber;
    /**
     * Atomic weight. "An atomic weight (relative atomic atomicWeight) of an
     * element from a specified source is the ratio of the average atomicWeight
     * per atom of the element to 1/12 of the atomicWeight of an atom of 12C"
     */
    public final double atomicWeight;
    /**
     * Valence number for this type.
     */
    public final int valence;

    /**
     * AtomType Constructor.
     *
     * @param type int
     * @param atomClass int
     * @param name String
     * @param environment String
     * @param atomicNumber int
     * @param atomicWeight double
     * @param valence int
     */
    public AtomType(int type, int atomClass, String name, String environment,
            int atomicNumber, double atomicWeight, int valence) {
        super(ForceField.ForceFieldType.ATOM, Integer.toString(type));
        this.type = type;
        this.atomClass = atomClass;
        this.name = name;
        this.environment = environment;
        this.atomicNumber = atomicNumber;
        this.atomicWeight = atomicWeight;
        this.valence = valence;
    }

    public void incrementClassAndType(int classIncrement, int typeIncrement) {
        atomClass += classIncrement;
        type += typeIncrement;
        setKey(Integer.toString(type));
    }

    /**
     * Nicely formatted atom type string.
     *
     * @return String
     */
    @Override
    public String toString() {
        String s;
        if (atomClass >= 0) {
            s = String.format("atom  %5d  %5d  %-4s  %-25s  %3d  %8.4f  %d",
                    type, atomClass, name, environment, atomicNumber,
                    atomicWeight, valence);
        } else {
            s = String.format("atom  %5d  %-4s  %-25s  %3d  %8.4f  %d", type,
                    name, environment, atomicNumber, atomicWeight, valence);
        }
        return s;
    }

    @Override
    public int compare(String s1, String s2) {

        int t1 = Integer.parseInt(s1);
        int t2 = Integer.parseInt(s2);

        if (t1 < t2) {
            return -1;
        }
        if (t1 > t2) {
            return 1;
        }

        return 0;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (other == null || !(other instanceof AtomType)) {
            return false;
        }
        AtomType atomType = (AtomType) other;
        if (atomType.type == this.type) {
            return true;
        }

        return false;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 71 * hash + this.type;
        return hash;
    }
}
