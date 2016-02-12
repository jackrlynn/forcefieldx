/**
 * Title: Force Field X.
 *
 * Description: Force Field X - Software for Molecular Biophysics.
 *
 * Copyright: Copyright (c) Michael J. Schnieders 2001-2016.
 *
 * This file is part of Force Field X.
 *
 * Force Field X is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 3 as published by
 * the Free Software Foundation.
 *
 * Force Field X is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * Force Field X; if not, write to the Free Software Foundation, Inc., 59 Temple
 * Place, Suite 330, Boston, MA 02111-1307 USA
 *
 * Linking this library statically or dynamically with other modules is making a
 * combined work based on this library. Thus, the terms and conditions of the
 * GNU General Public License cover the whole combination.
 *
 * As a special exception, the copyright holders of this library give you
 * permission to link this library with independent modules to produce an
 * executable, regardless of the license terms of these independent modules, and
 * to copy and distribute the resulting executable under terms of your choice,
 * provided that you also meet, for each linked independent module, the terms
 * and conditions of the license of that module. An independent module is a
 * module which is not derived from or based on this library. If you modify this
 * library, you may extend this exception to your version of the library, but
 * you are not obligated to do so. If you do not wish to do so, delete this
 * exception statement from your version.
 */
package ffx.potential.parameters;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;

import static org.apache.commons.math3.util.FastMath.pow;

/**
 * The PolarizeType class defines an isotropic atomic polarizability.
 *
 * @author Michael J. Schnieders
 * @since 1.0
 *
 */
public final class PolarizeType extends BaseType implements Comparator<String> {

    private static final double sixth = 1.0 / 6.0;
    /**
     * Atom type number.
     */
    public int type;
    /**
     * Thole damping factor.
     */
    public final double thole;
    /**
     * Value of polarizability scale factor.
     */
    public final double pdamp;
    /**
     * Isotropic polarizability in units of Angstroms^3.
     */
    public final double polarizability;
    /**
     * Connected types in the polarization group of each atom. (may be null)
     */
    public int[] polarizationGroup;

    /**
     * PolarizeType Constructor.
     *
     * @param atomType int
     * @param polarizability double
     * @param polarizationGroup int[]
     * @param thole a double.
     */
    public PolarizeType(int atomType, double polarizability, double thole,
            int polarizationGroup[]) {
        super(ForceField.ForceFieldType.POLARIZE, Integer.toString(atomType));
        this.type = atomType;
        this.thole = thole;
        this.polarizability = polarizability;
        this.polarizationGroup = polarizationGroup;
        if (thole == 0.0) {
            pdamp = 0.0;
        } else {
            pdamp = pow(polarizability, sixth);
        }
    }

    /**
     * <p>
     * incrementType</p>
     *
     * @param increment a int.
     */
    public void incrementType(int increment) {
        type += increment;
        setKey(Integer.toString(type));
        if (polarizationGroup != null) {
            for (int i = 0; i < polarizationGroup.length; i++) {
                polarizationGroup[i] += increment;
            }
        }
    }

    /**
     * Add mapped known types to the polarization group of a new patch.
     *
     * @param typeMap a lookup between new atom types and known atom types.
     * @return
     */
    public boolean patchTypes(HashMap<AtomType, AtomType> typeMap) {
        if (polarizationGroup == null) {
            return false;
        }

        /**
         * Append known mapped types.
         */
        int len = polarizationGroup.length;
        int added = 0;
        for (AtomType newType : typeMap.keySet()) {
            for (int i = 1; i < len; i++) {
                if (polarizationGroup[i] == newType.type) {
                    AtomType knownType = typeMap.get(newType);
                    added++;
                    polarizationGroup = Arrays.copyOf(polarizationGroup, len + added);
                    polarizationGroup[len + added - 1] = knownType.type;
                }
            }
        }
        if (added > 0) {
            return true;
        }
        return false;
    }

    /**
     * <p>
     * add</p>
     *
     * @param key a int.
     */
    public void add(int key) {
        for (int i : polarizationGroup) {
            if (key == i) {
                return;
            }
        }
        int len = polarizationGroup.length;
        int newGroup[] = new int[len + 1];
        for (int i = 0; i < len; i++) {
            newGroup[i] = polarizationGroup[i];
        }
        newGroup[len] = key;
        polarizationGroup = newGroup;
    }

    /**
     * {@inheritDoc}
     *
     * Nicely formatted polarization type.
     */
    @Override
    public String toString() {
        StringBuilder polarizeString = new StringBuilder(String.format(
                "polarize  %5d  %6.3f %6.3f", type, polarizability, thole));
        if (polarizationGroup != null) {
            for (int a : polarizationGroup) {
                polarizeString.append(String.format("  %5d", a));
            }
        }
        return polarizeString.toString();
    }

    /**
     * {@inheritDoc}
     */
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

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (other == null || !(other instanceof PolarizeType)) {
            return false;
        }
        PolarizeType polarizeType = (PolarizeType) other;
        if (polarizeType.type == this.type) {
            return true;
        }

        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        int hash = 5;
        hash = 37 * hash + type;
        return hash;
    }

    /**
     * Average two PolarizeType instances. The atom types to include in the new
     * polarizationGroup must be supplied.
     *
     * @param polarizeType1
     * @param polarizeType2
     * @param atomType
     * @param polarizationGroup
     * @return
     */
    public static PolarizeType average(PolarizeType polarizeType1,
            PolarizeType polarizeType2, int atomType, int polarizationGroup[]) {
        if (polarizeType1 == null || polarizeType2 == null) {
            return null;
        }
        double thole = (polarizeType1.thole + polarizeType2.thole) / 2.0;
        double polarizability = (polarizeType1.polarizability + polarizeType2.polarizability) / 2.0;
        return new PolarizeType(atomType, thole, polarizability, polarizationGroup);
    }

}