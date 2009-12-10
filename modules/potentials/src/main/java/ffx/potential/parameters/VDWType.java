/**
 * Title: Force Field X
 * Description: Force Field X - Software for Molecular Biophysics.
 * Copyright: Copyright (c) Michael J. Schnieders 2001-2009
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

/**
 * The VDWType class defines a van der Waals type.
 */
public final class VDWType extends BaseType {

    public enum RadiusSize {

        RADIUS, DIAMETER
    }

    public enum RadiusType {

        RMIN, SIGMA
    }
    /**
     * The atom class that uses this van der Waals parameter.
     */
    public final int atomClass;
    /**
     * The radius of the minimum well depth energy (angstroms).
     */
    public final double radius;
    /**
     * The minimum energy of the vdw function (kcal/mol).
     */
    public final double wellDepth;
    /**
     * Reduction factor for evaluating van der Waals pairs. Valid range: 0.0 >
     * reduction <= 1.0 Usually only hydrogen atom have a reduction factor.
     * Setting the reduction to < 0.0 indicates it is not being used.
     */
    public final double reductionFactor;

    /**
     * van der Waals constructor. If the reduction factor is <= 0.0, no
     * reduction is used for this atom type.
     *
     * @param atomClass
     *            int
     * @param radius
     *            double
     * @param wellDepth
     *            double
     * @param reductionFactor
     *            double
     */
    public VDWType(int atomClass, double radius, double wellDepth,
            double reductionFactor) {
        super(ForceField.ForceFieldType.VDW, new String("" + atomClass));
        this.atomClass = atomClass;
        this.radius = radius;
        this.wellDepth = wellDepth;
        this.reductionFactor = reductionFactor;
    }

    /**
     * Nicely formatted van der Waals type.
     *
     * @return String
     */
    @Override
    public String toString() {
        String vdwString;
        // No reduction factor.
        if (reductionFactor <= 0e0) {
            vdwString = String.format("vdw  %5d  %11.9f  %11.9f", atomClass,
                    radius, wellDepth);
        } else {
            vdwString = String.format("vdw  %5d  %11.9f  %11.9f  %5.3f",
                    atomClass, radius, wellDepth, reductionFactor);
        }
        return vdwString;
    }
}
