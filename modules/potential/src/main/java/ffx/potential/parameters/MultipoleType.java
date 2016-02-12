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
import java.util.logging.Logger;

import static org.apache.commons.math3.util.FastMath.abs;

/**
 * The MultipoleType class defines a multipole in its local frame.
 *
 * @author Michael J. Schnieders
 * @since 1.0
 *
 */
public final class MultipoleType extends BaseType implements Comparator<String> {

    private static final Logger logger = Logger.getLogger(MultipoleType.class.getName());

    /**
     * The local multipole frame is defined by the Z-then-X or Bisector
     * convention.
     */
    public enum MultipoleFrameDefinition {

        ZTHENX, BISECTOR, ZTHENBISECTOR, TRISECTOR
    }
    /**
     * Conversion from electron-Angstroms to Debyes
     */
    public static final double DEBYE = 4.80321;
    /**
     * Conversion from electron-Angstroms^2 to Buckinghams
     */
    public static final double BUCKINGHAM = DEBYE * DEBYE;
    /**
     * Conversion from Bohr to Angstroms
     */
    public static final double BOHR = 0.52917720859;
    /**
     * Conversion from Bohr^2 to Angstroms^2
     */
    public static final double BOHR2 = BOHR * BOHR;
    /**
     * Conversion from electron**2/Ang to kcal/mole.
     */
    public static final double ELECTRIC = 332.063709;
    /**
     * Partial atomic charge (e).
     */
    public final double charge;
    /**
     * Atomic dipole. 1 x 3 (e Angstroms).
     */
    public final double dipole[];
    /**
     * Atomic quadrupole. 3 x 3 (e Angstroms^2).
     */
    public final double quadrupole[][];
    /**
     * Local frame definition method.
     */
    public final MultipoleFrameDefinition frameDefinition;
    /**
     * Atom types that define the local frame of this multipole.
     */
    public final int[] frameAtomTypes;

    /**
     * Multipole Constructor. This assumes the dipole and quadrupole are in
     * units of Bohr, and are converted to electron-Angstroms and
     * electron-Angstroms^2, respectively, before the constructor returns.
     *
     * @param charge double
     * @param dipole double[]
     * @param quadrupole double[]
     * @param multipoleFrameTypes int[]
     * @param frameDefinition a
     * {@link ffx.potential.parameters.MultipoleType.MultipoleFrameDefinition}
     * object.
     */
    public MultipoleType(double charge, double dipole[], double quadrupole[][],
            int[] multipoleFrameTypes, MultipoleFrameDefinition frameDefinition) {
        super(ForceField.ForceFieldType.MULTIPOLE, multipoleFrameTypes);
        this.charge = charge;
        this.dipole = dipole;
        this.quadrupole = quadrupole;
        this.frameAtomTypes = multipoleFrameTypes;
        this.frameDefinition = frameDefinition;
        initMultipole();
    }

    /**
     * <p>
     * incrementType</p>
     *
     * @param increment a int.
     */
    public void incrementType(int increment) {
        for (int i = 0; i < frameAtomTypes.length; i++) {
            // Frame atom types of 0 are unchanged.
            if (frameAtomTypes[i] > 0) {
                frameAtomTypes[i] += increment;
            } else if (frameAtomTypes[i] < 0) {
                frameAtomTypes[i] -= increment;
            }
        }
        setKey(frameAtomTypes);
    }

    /**
     * Remap new atom types to known internal ones.
     *
     * @param typeMap a lookup between new atom types and known atom types.
     *
     * @return
     */
    public MultipoleType patchTypes(HashMap<AtomType, AtomType> typeMap) {
        int count = 0;
        int len = frameAtomTypes.length;
        /**
         * Look for a MultipoleType that contain a mapped atom class.
         */
        for (AtomType newType : typeMap.keySet()) {
            for (int i = 0; i < len; i++) {
                if (frameAtomTypes[i] == newType.type) {
                    count++;
                }
            }
        }
        /**
         * If found, create a new MultipoleType that bridges to known classes.
         */
        if (count > 0 && count < len) {
            int newFrame[] = Arrays.copyOf(frameAtomTypes, len);
            for (AtomType newType : typeMap.keySet()) {
                for (int i = 0; i < len; i++) {
                    if (frameAtomTypes[i] == newType.type) {
                        AtomType knownType = typeMap.get(newType);
                        newFrame[i] = knownType.type;
                    }
                }
            }
            return new MultipoleType(charge, dipole, quadrupole, newFrame, frameDefinition);
        }
        return null;
    }

    private void initMultipole() {
        // Check symmetry.
        double check = Math.abs(quadrupole[0][1] - quadrupole[1][0]);
        if (check > 1.0e-6) {
            logger.warning("Multipole component Qxy != Qyx");
            print();
        }
        check = Math.abs(quadrupole[0][2] - quadrupole[2][0]);
        if (check > 1.0e-6) {
            logger.warning("Multipole component Qxz != Qzx");
            print();
        }
        check = Math.abs(quadrupole[1][2] - quadrupole[2][1]);
        if (check > 1.0e-6) {
            logger.warning("Multipole component Qyz != Qzy");
            print();
        }
        // Convert to electron-Angstroms
        for (int i = 0; i < 3; i++) {
            dipole[i] *= BOHR;
        }
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                quadrupole[i][j] *= BOHR * BOHR;
            }
        }
        // Warn if the multipole is not traceless.
        double sum = quadrupole[0][0] + quadrupole[1][1] + quadrupole[2][2];
        if (Math.abs(sum) > 1.0e-5) {
            String message = String.format("Multipole is not traceless: %7.5f",
                    sum);
            logger.warning(message + "\n" + toBohrString());
        }
    }

    /**
     * Nicely formatted multipole string. Dipole and qaudrupole are in
     * electron-Bohrs and electron-Bohrs^2, respectively.
     *
     * @return String
     */
    public String toBohrString() {
        StringBuilder multipoleBuffer = new StringBuilder("multipole");
        if (frameDefinition == MultipoleFrameDefinition.BISECTOR) {
            multipoleBuffer.append(String.format("  %5d", frameAtomTypes[0]));
            multipoleBuffer.append(String.format("  %5d", -frameAtomTypes[1]));
            multipoleBuffer.append(String.format("  %5d", -frameAtomTypes[2]));
        } else if (frameDefinition == MultipoleFrameDefinition.ZTHENBISECTOR) {
            multipoleBuffer.append(String.format("  %5d", frameAtomTypes[0]));
            multipoleBuffer.append(String.format("  %5d", frameAtomTypes[1]));
            multipoleBuffer.append(String.format("  %5d", -frameAtomTypes[2]));
            multipoleBuffer.append(String.format("  %5d", -frameAtomTypes[3]));
        } else if (frameDefinition == MultipoleFrameDefinition.TRISECTOR) {
            multipoleBuffer.append(String.format("  %5d", frameAtomTypes[0]));
            multipoleBuffer.append(String.format("  %5d", -frameAtomTypes[1]));
            multipoleBuffer.append(String.format("  %5d", -frameAtomTypes[2]));
            multipoleBuffer.append(String.format("  %5d", -frameAtomTypes[3]));
        } else {
            for (int i : frameAtomTypes) {
                multipoleBuffer.append(String.format("  %5d", i));
            }
        }
        if (frameAtomTypes.length == 3) {
            multipoleBuffer.append("       ");
        }
        multipoleBuffer.append(String.format("  % 7.5f \\\n"
                + "%11$s % 7.5f % 7.5f % 7.5f \\\n" + "%11$s % 7.5f \\\n"
                + "%11$s % 7.5f % 7.5f \\\n" + "%11$s % 7.5f % 7.5f % 7.5f",
                charge, dipole[0] / BOHR, dipole[1] / BOHR, dipole[2] / BOHR,
                quadrupole[0][0] / BOHR2, quadrupole[1][0] / BOHR2,
                quadrupole[1][1] / BOHR2, quadrupole[2][0] / BOHR2,
                quadrupole[2][1] / BOHR2, quadrupole[2][2] / BOHR2,
                "                                      "));
        return multipoleBuffer.toString();
    }

    /**
     * Nicely formatted multipole string. Dipole and qaudrupole are in units of
     * Debye and Buckinghams, respectively.
     *
     * @return String
     */
    public String toDebyeString() {
        StringBuilder multipoleBuffer = new StringBuilder("multipole");
        if (frameDefinition == MultipoleFrameDefinition.BISECTOR) {
            multipoleBuffer.append(String.format("  %5d", frameAtomTypes[0]));
            multipoleBuffer.append(String.format("  %5d", -frameAtomTypes[1]));
            multipoleBuffer.append(String.format("  %5d", -frameAtomTypes[2]));
        } else if (frameDefinition == MultipoleFrameDefinition.ZTHENBISECTOR) {
            multipoleBuffer.append(String.format("  %5d", frameAtomTypes[0]));
            multipoleBuffer.append(String.format("  %5d", frameAtomTypes[1]));
            multipoleBuffer.append(String.format("  %5d", -frameAtomTypes[2]));
            multipoleBuffer.append(String.format("  %5d", -frameAtomTypes[3]));
        } else if (frameDefinition == MultipoleFrameDefinition.TRISECTOR) {
            multipoleBuffer.append(String.format("  %5d", frameAtomTypes[0]));
            multipoleBuffer.append(String.format("  %5d", -frameAtomTypes[1]));
            multipoleBuffer.append(String.format("  %5d", -frameAtomTypes[2]));
            multipoleBuffer.append(String.format("  %5d", -frameAtomTypes[3]));
        } else {
            for (int i : frameAtomTypes) {
                multipoleBuffer.append(String.format("  %5d", i));
            }
        }
        if (frameAtomTypes.length == 3) {
            multipoleBuffer.append("       ");
        }
        multipoleBuffer.append(String.format("  % 7.5f\\\n"
                + "%11$s % 7.5f % 7.5f % 7.5f\\\n" + "%11$s % 7.5f\\\n"
                + "%11$s % 7.5f % 7.5f\\\n" + "%11$s % 7.5f % 7.5f % 7.5f",
                charge, dipole[0] * DEBYE, dipole[1] * DEBYE,
                dipole[2] * DEBYE, quadrupole[0][0] * BUCKINGHAM,
                quadrupole[1][0] * BUCKINGHAM, quadrupole[1][1] * BUCKINGHAM,
                quadrupole[2][0] * BUCKINGHAM, quadrupole[2][1] * BUCKINGHAM,
                quadrupole[2][2] * BUCKINGHAM,
                "                                      "));
        return multipoleBuffer.toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return toBohrString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int compare(String s1, String s2) {
        String keys1[] = s1.split(" ");
        String keys2[] = s2.split(" ");

        int len = keys1.length;
        if (keys1.length > keys2.length) {
            len = keys2.length;
        }
        int c1[] = new int[len];
        int c2[] = new int[len];
        for (int i = 0; i < len; i++) {
            c1[i] = abs(Integer.parseInt(keys1[i]));
            c2[i] = abs(Integer.parseInt(keys2[i]));
            if (c1[i] < c2[i]) {
                return -1;
            } else if (c1[i] > c2[i]) {
                return 1;
            }
        }

        if (keys1.length < keys2.length) {
            return -1;
        } else if (keys1.length > keys2.length) {
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
        if (other == null || !(other instanceof MultipoleType)) {
            return false;
        }
        MultipoleType multipoleType = (MultipoleType) other;
        int c[] = multipoleType.frameAtomTypes;
        if (c.length != frameAtomTypes.length) {
            return false;
        }
        for (int i = 0; i < c.length; i++) {
            if (c[i] != this.frameAtomTypes[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        int hash = 7;
        hash = 29 * hash + Arrays.hashCode(frameAtomTypes);
        return hash;
    }

    /**
     * Average two MultipoleType instances. The atom types that define the frame
     * of the new type must be supplied.
     *
     * @param multipoleType1
     * @param multipoleType2
     * @param multipoleFrameTypes
     * @return
     */
    public static MultipoleType average(MultipoleType multipoleType1, MultipoleType multipoleType2, int[] multipoleFrameTypes) {
        if (multipoleType1 == null || multipoleType2 == null || multipoleFrameTypes != null) {
            return null;
        }
        MultipoleFrameDefinition frameDefinition = multipoleType1.frameDefinition;
        if (frameDefinition != multipoleType1.frameDefinition) {
            return null;
        }
        double charge = (multipoleType1.charge + multipoleType2.charge) / 2.0;
        double[] dipole = new double[3];
        double[][] quadrupole = new double[3][3];
        for (int i = 0; i < 3; i++) {
            dipole[i] = (multipoleType1.dipole[i] + multipoleType2.dipole[i]) / 2.0;
            for (int j = 0; j < 3; j++) {
                quadrupole[i][j] = (multipoleType1.quadrupole[i][j] + multipoleType2.quadrupole[i][j]) / 2.0;
            }
        }
        return new MultipoleType(charge, dipole, quadrupole, multipoleFrameTypes, frameDefinition);
    }

    /**
     * Indices into a 1D tensor array based on compressed tensor notation. This
     * makes multipole code much easier to read.
     */
    public static final int t000 = 0;
    /**
     * Constant <code>t100=1</code>
     */
    public static final int t100 = 1;
    /**
     * Constant <code>t010=2</code>
     */
    public static final int t010 = 2;
    /**
     * Constant <code>t001=3</code>
     */
    public static final int t001 = 3;
    /**
     * Constant <code>t200=4</code>
     */
    public static final int t200 = 4;
    /**
     * Constant <code>t020=5</code>
     */
    public static final int t020 = 5;
    /**
     * Constant <code>t002=6</code>
     */
    public static final int t002 = 6;
    /**
     * Constant <code>t110=7</code>
     */
    public static final int t110 = 7;
    /**
     * Constant <code>t101=8</code>
     */
    public static final int t101 = 8;
    /**
     * Constant <code>t011=9</code>
     */
    public static final int t011 = 9;
    /**
     * Constant <code>t300=10</code>
     */
    public static final int t300 = 10;
    /**
     * Constant <code>t030=11</code>
     */
    public static final int t030 = 11;
    /**
     * Constant <code>t003=12</code>
     */
    public static final int t003 = 12;
    /**
     * Constant <code>t210=13</code>
     */
    public static final int t210 = 13;
    /**
     * Constant <code>t201=14</code>
     */
    public static final int t201 = 14;
    /**
     * Constant <code>t120=15</code>
     */
    public static final int t120 = 15;
    /**
     * Constant <code>t021=16</code>
     */
    public static final int t021 = 16;
    /**
     * Constant <code>t102=17</code>
     */
    public static final int t102 = 17;
    /**
     * Constant <code>t012=18</code>
     */
    public static final int t012 = 18;
    /**
     * Constant <code>t111=19</code>
     */
    public static final int t111 = 19;
}