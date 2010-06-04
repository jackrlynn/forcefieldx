/**
 * Title: Force Field X
 * Description: Force Field X - Software for Molecular Biophysics.
 * Copyright: Copyright (c) Michael J. Schnieders 2001-2010
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

import static java.lang.Math.PI;
import static java.lang.Math.pow;

import java.util.Arrays;
import java.util.Comparator;

/**
 * The OutOfPlaneBendType class defines one Allinger style
 * out-of-plane angle bending energy type.
 *
 * @author Michael J. Schnieders
 *
 * @since 1.0
 */
public final class OutOfPlaneBendType extends BaseType implements Comparator<String> {

    /**
     * Atom classes for this out-of-plane angle bending type.
     */
    public final int atomClasses[];
    /**
     * Force constant (Kcal/mol/Angstrom).
     */
    public final double forceConstant;

    /**
     * OutOfPlaneBendType Constructor.
     *
     * @param atomClasses
     *            int[]
     * @param forceConstant
     *            double
     */
    public OutOfPlaneBendType(int atomClasses[], double forceConstant) {
        super(ForceField.ForceFieldType.OPBEND, sortKey(atomClasses));
        this.atomClasses = atomClasses;
        this.forceConstant = forceConstant;
    }

    /**
     * This method sorts the atom classes for the out-of-plane angle bending type.
     *
     * @param c
     *            atomClasses
     * @return lookup key
     */
    public static String sortKey(int c[]) {
        if (c == null || c.length != 4) {
            return null;
        }
        String key = c[0] + " " + c[1] + " " + c[2] + " " + c[3];
        return key;
    }

    /**
     * Nicely formatted out-of-plane angle bending string.
     *
     * @return String
     */
    @Override
    public String toString() {
        return String.format("opbend  %5d  %5d  %5d  %5d  %6.2f", atomClasses[0],
                atomClasses[1], atomClasses[2],
                atomClasses[3], forceConstant);
    }
    /**
     * Cubic coefficient in out-of-plane angle bending potential.
     */
    public static final double cubic = -0.014;
    /**
     * Quartic coefficient in out-of-plane angle bending potential.
     */
    public static final double quartic = 0.000056;
    /**
     * Quintic coefficient in out-of-plane angle bending potential.
     */
    public static final double quintic = -0.0000007;
    /**
     * Sextic coefficient in out-of-plane angle bending potential.
     */
    public static final double sextic = 0.000000022;
    /**
     * Convert Out-of-Plane bending energy to kcal/mole.
     */
    public static final double units = 1.0 / pow(180.0 / PI, 2);

    @Override
    public int compare(String s1, String s2) {
        String keys1[] = s1.split(" ");
        String keys2[] = s2.split(" ");

        for (int i = 0; i < 4; i++) {
            int c1 = Integer.parseInt(keys1[i]);
            int c2 = Integer.parseInt(keys2[i]);
            if (c1 < c2) {
                return -1;
            } else if (c1 > c2) {
                return 1;
            }
        }
        return 0;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (other == null || !(other instanceof OutOfPlaneBendType)) {
            return false;
        }
        OutOfPlaneBendType outOfPlaneBendType = (OutOfPlaneBendType) other;
        for (int i = 0; i < 4; i++) {
            if (outOfPlaneBendType.atomClasses[i] != atomClasses[i]) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 53 * hash + Arrays.hashCode(atomClasses);
        return hash;
    }
}
