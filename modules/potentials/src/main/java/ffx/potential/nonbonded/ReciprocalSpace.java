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
package ffx.potential.nonbonded;

import static ffx.numerics.UniformBSpline.bSpline;
import static ffx.numerics.UniformBSpline.bSplineDerivatives;
import static java.lang.Math.PI;
import static java.lang.Math.cos;
import static java.lang.Math.exp;
import static java.lang.Math.pow;
import static java.lang.Math.round;
import static java.lang.Math.signum;
import static java.lang.Math.sin;

import java.util.logging.Logger;

import edu.rit.pj.IntegerForLoop;
import edu.rit.pj.IntegerSchedule;
import edu.rit.pj.ParallelRegion;
import edu.rit.pj.ParallelTeam;
import ffx.crystal.Crystal;
import ffx.numerics.TensorRecursion;
import ffx.numerics.fft.Complex;
import ffx.numerics.fft.Complex3DParallel;
import ffx.numerics.fft.Real3DParallel;
import ffx.potential.parameters.ForceField;
import ffx.potential.parameters.ForceField.ForceFieldDouble;
import ffx.potential.parameters.ForceField.ForceFieldInteger;
import java.util.logging.Level;

/**
 * The Reciprocal Space class computes the reciprocal space contribution to
 * {@link ParticleMeshEwald} for the AMOEBA force field.
 *
 * <ol>
 * <li>
 * Assignment of polarizable multipole charge density to the 3D grid,
 * via b-Splines, is parallelized using a spatial decomposition.
 * </li>
 * <p>
 * <li>
 * The convolution depends on methods of the {@link Real3DParallel} and
 * {@link Complex3DParallel} classes.
 * </li>
 * <p>
 * <li>
 * Finally, the electric potential and its gradients are collected,
 * in parallel, off the grid using b-Splines.
 * </li>
 * </ol>
 *
 * @author Michael J. Schnieders
 * @since 1.0
 */
public class ReciprocalSpace {

    private static final Logger logger = Logger.getLogger(ReciprocalSpace.class.getName());
    private final Crystal crystal;
    private final int nSymm;
    private final double coordinates[][][];
    private final double xf[];
    private final double yf[];
    private final double zf[];
    private final int nAtoms;
    private final double fractionalMultipole[][][];
    private final double fractionalDipole[][][];
    private final double fractionalDipolep[][][];
    private final double fractionalMultipolePhi[][];
    private final double fractionalInducedDipolePhi[][];
    private final double fractionalInducedDipolepPhi[][];
    private final int fftX, fftY, fftZ;
    private final int halfFFTX, halfFFTY, halfFFTZ;
    private final int xSide;
    private final int xySlice;
    private final int zSlicep;
    private final int nfftTotal;
    private final int polarizationTotal;
    private final double aewald;
    private final int bSplineOrder;
    private final int derivOrder = 3;
    private final double densityGrid[];
    /**
     * The number of divisions along the A-axis.
     */
    private int nA;
    /**
     * The number of divisions along the B-axis.
     */
    private int nB;
    /**
     * The number of divisions along the C-Axis.
     */
    private int nC;
    /**
     * The number of cells in one plane (nDivisions^2).
     */
    private int nAB;
    /**
     * The number of cells (nDivisions^3).
     */
    private final int nCells;
    private final int nWork;
    /**
     * A temporary array that holds the index of the cell each atom is assigned
     * to.
     */
    private final int cellIndex[][];
    /**
     * The cell indices of each atom along a A-axis.
     */
    private final int cellA[];
    /**
     * The cell indices of each atom along a B-axis.
     */
    private final int cellB[];
    /**
     * The cell indices of each atom along a C-axis.
     */
    private final int cellC[];
    /**
     * The cell indices of each atom along a A-axis.
     */
    private final int workA[];
    /**
     * The cell indices of each atom along a B-axis.
     */
    private final int workB[];
    /**
     * The cell indices of each atom along a C-axis.
     */
    private final int workC[];
    /**
     * The list of atoms in each cell. [nsymm][natom] = atom index
     */
    private final int cellList[][];
    /**
     * The offset of each atom from the start of the cell. The first atom atom
     * in the cell has 0 offset. [nsymm][natom] = offset of the atom
     */
    private final int cellOffset[][];
    /**
     * The number of atoms in each cell. [nsymm][ncell]
     */
    private final int cellCount[][];
    /**
     * The index of the first atom in each cell. [nsymm][ncell]
     */
    private final int cellStart[][];
    private final ParallelTeam parallelTeam;
    private final int threadCount;
    private final BSplineRegion bSplineRegion;
    private final PermanentDensityRegion permanentDensity;
    private final Real3DParallel realFFT3D;
    private final Complex3DParallel complexFFT3D;
    private final PermanentReciprocalSumRegion permanentReciprocalSum;
    private final PermanentPhiRegion permanentPhi;
    private final PolarizationDensityRegion polarizationDensity;
    private final PolarizationReciprocalSumRegion polarizationReciprocalSum;
    private final PolarizationPhiRegion polarizationPhi;

    /**
     * Reciprocal Space PME contribution.
     */
    public ReciprocalSpace(Crystal crystal, ForceField forceField,
            double coordinates[][][],
            int nAtoms, ParallelTeam parallelTeam, double aewald) {
        this.crystal = crystal;
        this.coordinates = coordinates;
        this.nAtoms = nAtoms;
        this.parallelTeam = parallelTeam;
        this.aewald = aewald;
        threadCount = parallelTeam.getThreadCount();

        bSplineOrder = forceField.getInteger(ForceFieldInteger.PME_ORDER, 6);
        double density = forceField.getDouble(ForceFieldDouble.PME_SPACING, 1.0);

        // Set default FFT grid size from unit cell dimensions.
        int nX = (int) Math.floor(crystal.a * density) + 1;
        int nY = (int) Math.floor(crystal.b * density) + 1;
        int nZ = (int) Math.floor(crystal.c * density) + 1;
        if (nX % 2 != 0) {
            nX += 1;
        }
        // Use preferred dimensions.
        while (!Complex.preferredDimension(nX)) {
            nX += 2;
        }
        while (!Complex.preferredDimension(nY)) {
            nY += 1;
        }
        while (!Complex.preferredDimension(nZ)) {
            nZ += 1;
        }

        fftX = nX;
        fftY = nY;
        fftZ = nZ;
        halfFFTX = nX / 2;
        halfFFTY = nY / 2;
        halfFFTZ = nZ / 2;
        xSide = (fftX + 2) * 2;
        xySlice = xSide * fftY;
        nfftTotal = (fftX + 2) * fftY * fftZ;
        zSlicep = fftX * fftY * 2;
        polarizationTotal = fftX * fftY * fftZ;
        a = new double[3][3];
        this.nSymm = crystal.spaceGroup.symOps.size();
        densityGrid = new double[polarizationTotal * 2];
        /**
         * Chop up the 3D unit cell domain into fractional coordinate chunks to
         * allow multiple threads to put charge density onto the grid without
         * needing the same grid point. First, we partition the X-axis, then
         * the Y-axis, and finally the Z-axis if necesary.
         */
        nX = 1;
        nY = 1;
        nZ = 1;
        int div = 1;
        if (threadCount > 1) {
            nZ = fftZ / bSplineOrder;
            if (nZ % 2 != 0) {
                nZ--;
            }
            nC = nZ;
            div *= 2;
            // If we have 2 * threadCount chunks, stop dividing the domain.
            if (nC / threadCount >= div) {
                nA = 1;
                nB = 1;
            } else {
                nY = fftY / bSplineOrder;
                if (nY % 2 != 0) {
                    nY--;
                }
                nB = nY;
                div *= 2;
                // If we have 4 * threadCount chunks, stop dividing the domain.
                if (nB * nC / threadCount >= div) {
                    nA = 1;
                } else {
                    nX = fftX / bSplineOrder;
                    if (nX % 2 != 0) {
                        nX--;
                    }
                    nA = nX;
                    div *= 2;
                }
            }
            nAB = nA * nB;
            nCells = nAB * nC;
            nWork = nA * nB * nC / div;
        } else {
            nA = 1;
            nB = 1;
            nC = 1;
            nAB = 1;
            nCells = 1;
            nWork = 1;
        }
        if (logger.isLoggable(Level.INFO)) {
            StringBuffer sb = new StringBuffer();
            sb.append(String.format(" B-Spline order:         %8d\n", bSplineOrder));
            sb.append(String.format(" Grid density:           %8.3f\n", density));
            sb.append(String.format(" Grid dimensions:           (%d,%d,%d)\n", fftX, fftY, fftZ));
            sb.append(String.format(" Grid chunks:               (%dx%dx%d)/%d = %d\n",
                    nA, nB, nC, div, nWork));
            logger.info(sb.toString());
        }

        workA = new int[nWork];
        workB = new int[nWork];
        workC = new int[nWork];
        int index = 0;
        for (int h = 0; h < nA; h += 2) {
            for (int k = 0; k < nB; k += 2) {
                for (int l = 0; l < nC; l += 2) {
                    workA[index] = h;
                    workB[index] = k;
                    workC[index++] = l;
                }
            }
        }
        cellList = new int[nSymm][nAtoms];
        cellIndex = new int[nSymm][nAtoms];
        cellOffset = new int[nSymm][nAtoms];
        cellStart = new int[nSymm][nCells];
        cellCount = new int[nSymm][nCells];
        cellA = new int[nAtoms];
        cellB = new int[nAtoms];
        cellC = new int[nAtoms];
        fractionalMultipole = new double[nSymm][nAtoms][10];
        fractionalDipole = new double[nSymm][nAtoms][3];
        fractionalDipolep = new double[nSymm][nAtoms][3];
        fractionalMultipolePhi = new double[nAtoms][tensorCount];
        fractionalInducedDipolePhi = new double[nAtoms][tensorCount];
        fractionalInducedDipolepPhi = new double[nAtoms][tensorCount];
        transformMultipoleMatrix(tmm);
        transformFieldMatrix(tfm);
        xf = new double[nAtoms];
        yf = new double[nAtoms];
        zf = new double[nAtoms];

        bSplineRegion = new BSplineRegion();
        realFFT3D = new Real3DParallel(fftX, fftY, fftZ, parallelTeam);
        complexFFT3D = new Complex3DParallel(fftX, fftY, fftZ, parallelTeam);
        permanentDensity = new PermanentDensityRegion(bSplineRegion);
        permanentReciprocalSum = new PermanentReciprocalSumRegion();
        permanentPhi = new PermanentPhiRegion(bSplineRegion);
        polarizationDensity = new PolarizationDensityRegion(bSplineRegion);
        polarizationReciprocalSum = new PolarizationReciprocalSumRegion();
        polarizationPhi = new PolarizationPhiRegion(bSplineRegion);
    }

    /**
     * Note that the Java function "signum" and the FORTRAN version have
     * different definitions for an argument of zero.
     * <p>
     * JAVA: Math.signum(0.0) == 0.0
     * <p>
     * FORTRAN: signum(0.0) .eq. 1.0
     */
    public void permanent(double globalMultipoles[][][]) {
        assignAtomsToCells();
        permanentDensity.setPermanent(globalMultipoles);
        try {
            long startTime = System.nanoTime();
            parallelTeam.execute(bSplineRegion);
            long bSplineTime = System.nanoTime();
            parallelTeam.execute(permanentDensity);
            long permanentDensityTime = System.nanoTime();
            realFFT3D.convolution(densityGrid, permanentReciprocalSum.getRecip());
            long convolutionTime = System.nanoTime();
            parallelTeam.execute(permanentPhi);
            long phiTime = System.nanoTime();

            phiTime -= convolutionTime;
            convolutionTime -= permanentDensityTime;
            permanentDensityTime -= bSplineTime;
            bSplineTime -= startTime;

            if (logger.isLoggable(Level.FINE)) {
                StringBuffer sb = new StringBuffer();
                sb.append(String.format("\nCompute B-Splines:      %8.3f (sec)\n", bSplineTime * toSeconds));
                sb.append(String.format("Grid Permanent Density: %8.3f (sec)\n", permanentDensityTime * toSeconds));
                sb.append(String.format("Convolution:            %8.3f (sec)\n", convolutionTime * toSeconds));
                sb.append(String.format("Compute Phi:            %8.3f (sec)\n", phiTime * toSeconds));
                logger.fine(sb.toString());
            }
        } catch (Exception e) {
            String message = "Fatal exception evaluating permanent reciprocal space potential.\n";
            logger.log(Level.SEVERE, message, e);
            System.exit(-1);
        }
    }
    private static double toSeconds = 0.000000001;

    /**
     * Note that the Java function "signum" and the FORTRAN version have
     * different definitions for an argument of zero.
     * <p>
     * JAVA: Math.signum(0.0) == 0.0
     * <p>
     * FORTRAN: signum(0.0) .eq. 1.0
     */
    public void polarization(double inducedDipole[][][],
            double inducedDipolep[][][]) {
        for (int i = 0; i < 3; i++) {
            a[0][i] = fftX * crystal.recip[i][0];
            a[1][i] = fftY * crystal.recip[i][1];
            a[2][i] = fftZ * crystal.recip[i][2];
        }
        polarizationDensity.setPolarization(inducedDipole, inducedDipolep);
        try {
            parallelTeam.execute(polarizationDensity);
            complexFFT3D.convolution(densityGrid, polarizationReciprocalSum.getRecip());
            parallelTeam.execute(polarizationPhi);
        } catch (Exception e) {
            String message = "Fatal exception evaluating induced reciprocal space field.\n";
            logger.log(Level.SEVERE, message, e);
            System.exit(-1);
        }
    }

    public void cartesianToFractionalDipoles(double inducedDipole[][][],
            double inducedDipolep[][][]) {
        for (int i = 0; i < 3; i++) {
            a[0][i] = fftX * crystal.recip[i][0];
            a[1][i] = fftY * crystal.recip[i][1];
            a[2][i] = fftZ * crystal.recip[i][2];
        }
        for (int iSymm = 0; iSymm < nSymm; iSymm++) {
            for (int i = 0; i < nAtoms; i++) {
                double in[] = inducedDipole[iSymm][i];
                double out[] = fractionalDipole[iSymm][i];
                out[0] = a[0][0] * in[0] + a[0][1] * in[1] + a[0][2] * in[2];
                out[1] = a[1][0] * in[0] + a[1][1] * in[1] + a[1][2] * in[2];
                out[2] = a[2][0] * in[0] + a[2][1] * in[1] + a[2][2] * in[2];
                in = inducedDipolep[iSymm][i];
                out = fractionalDipolep[iSymm][i];
                out[0] = a[0][0] * in[0] + a[0][1] * in[1] + a[0][2] * in[2];
                out[1] = a[1][0] * in[0] + a[1][1] * in[1] + a[1][2] * in[2];
                out[2] = a[2][0] * in[0] + a[2][1] * in[1] + a[2][2] * in[2];
            }
        }
    }

    public double[][] getFractionalMultipolePhi() {
        return fractionalMultipolePhi;
    }

    public double[][] getFractionalMultipoles() {
        return fractionalMultipole[0];
    }

    public double[][] getFractionalInducedDipolePhi() {
        return fractionalInducedDipolePhi;
    }

    public double[][] getFractionalInducedDipoles() {
        return this.fractionalDipole[0];
    }

    public double[][] getFractionalInducedDipolepPhi() {
        return fractionalInducedDipolepPhi;
    }

    public double[][] getFractionalInducedDipolesp() {
        return this.fractionalDipolep[0];
    }

    public void getCartesianMultipolePhi(double cartesianMultipolePhi[][]) {
        fractionalToCartesianPhi(fractionalMultipolePhi, cartesianMultipolePhi);
    }

    public void getCartesianPolarizationPhi(double cartesianPolarizationPhi[][]) {
        fractionalToCartesianPhi(fractionalInducedDipolePhi,
                cartesianPolarizationPhi);
    }

    public void getCartesianChainRulePhi(double cartesianChainRulePhi[][]) {
        fractionalToCartesianPhi(fractionalInducedDipolepPhi,
                cartesianChainRulePhi);
    }

    public double getNfftX() {
        return fftX;
    }

    public double getNfftY() {
        return fftY;
    }

    public double getNfftZ() {
        return fftZ;
    }

    private class BSplineRegion extends ParallelRegion {

        private final BSplineFillLoop bSplineFillLoop[];
        public final double splineX[][][][];
        public final double splineY[][][][];
        public final double splineZ[][][][];
        public final int initGrid[][][];

        public BSplineRegion() {
            bSplineFillLoop = new BSplineFillLoop[threadCount];
            for (int i = 0; i < threadCount; i++) {
                bSplineFillLoop[i] = new BSplineFillLoop();
            }
            initGrid = new int[nSymm][nAtoms][3];
            splineX = new double[nSymm][nAtoms][bSplineOrder][derivOrder + 1];
            splineY = new double[nSymm][nAtoms][bSplineOrder][derivOrder + 1];
            splineZ = new double[nSymm][nAtoms][bSplineOrder][derivOrder + 1];
        }

        @Override
        public void run() {
            try {
                execute(0, nAtoms - 1, bSplineFillLoop[getThreadIndex()]);
            } catch (Exception e) {
                logger.severe(e.toString());
            }
        }

        private class BSplineFillLoop extends IntegerForLoop {

            private final double r00;
            private final double r01;
            private final double r02;
            private final double r10;
            private final double r11;
            private final double r12;
            private final double r20;
            private final double r21;
            private final double r22;
            private final double bSplineWork[][];
            private final IntegerSchedule schedule = IntegerSchedule.fixed();
            // 128 bytes of extra padding to avert cache interference.
            private long p0, p1, p2, p3, p4, p5, p6, p7;
            private long p8, p9, pa, pb, pc, pd, pe, pf;

            @Override
            public IntegerSchedule schedule() {
                return schedule;
            }

            public BSplineFillLoop() {
                super();
                r00 = crystal.recip[0][0];
                r01 = crystal.recip[0][1];
                r02 = crystal.recip[0][2];
                r10 = crystal.recip[1][0];
                r11 = crystal.recip[1][1];
                r12 = crystal.recip[1][2];
                r20 = crystal.recip[2][0];
                r21 = crystal.recip[2][1];
                r22 = crystal.recip[2][2];
                bSplineWork = new double[bSplineOrder][bSplineOrder];
            }

            @Override
            public void run(int lb, int ub) {
                for (int iSymOp = 0; iSymOp < nSymm; iSymOp++) {
                    final double x[] = coordinates[iSymOp][0];
                    final double y[] = coordinates[iSymOp][1];
                    final double z[] = coordinates[iSymOp][2];
                    final int initgridi[][] = initGrid[iSymOp];
                    final double splineXi[][][] = splineX[iSymOp];
                    final double splineYi[][][] = splineY[iSymOp];
                    final double splineZi[][][] = splineZ[iSymOp];
                    for (int i = lb; i <= ub; i++) {
                        final double xi = x[i];
                        final double yi = y[i];
                        final double zi = z[i];
                        final int[] grd = initgridi[i];
                        final double wx = xi * r00 + yi * r10 + zi * r20;
                        final double ux = wx - round(wx) + 0.5;
                        final double frx = fftX * ux;
                        final int ifrx = (int) frx;
                        final double bx = frx - ifrx;
                        grd[0] = ifrx - bSplineOrder;
                        bSplineDerivatives(bx, bSplineOrder, derivOrder, splineXi[i],
                                bSplineWork);
                        final double wy = xi * r01 + yi * r11 + zi * r21;
                        final double uy = wy - round(wy) + 0.5;
                        final double fry = fftY * uy;
                        final int ifry = (int) fry;
                        final double by = fry - ifry;
                        grd[1] = ifry - bSplineOrder;
                        bSplineDerivatives(by, bSplineOrder, derivOrder, splineYi[i],
                                bSplineWork);
                        final double wz = xi * r02 + yi * r12 + zi * r22;
                        final double uz = wz - round(wz) + 0.5;
                        final double frz = fftZ * uz;
                        final int ifrz = (int) frz;
                        final double bz = frz - ifrz;
                        grd[2] = ifrz - bSplineOrder;
                        bSplineDerivatives(bz, bSplineOrder, derivOrder, splineZi[i],
                                bSplineWork);
                    }
                }
            }
        }
    }

    private class PermanentDensityRegion extends ParallelRegion {

        private final GridInitLoop gridInitLoop;
        private final PermanentDensityLoop permanentDensityLoop[];
        private final double splineX[][][][];
        private final double splineY[][][][];
        private final double splineZ[][][][];
        private final int initGrid[][][];

        public PermanentDensityRegion(BSplineRegion bSplineRegion) {
            this.initGrid = bSplineRegion.initGrid;
            this.splineX = bSplineRegion.splineX;
            this.splineY = bSplineRegion.splineY;
            this.splineZ = bSplineRegion.splineZ;
            gridInitLoop = new GridInitLoop();
            permanentDensityLoop = new PermanentDensityLoop[threadCount];
            for (int i = 0; i < threadCount; i++) {
                permanentDensityLoop[i] = new PermanentDensityLoop();
            }
        }

        public void setPermanent(double globalMultipoles[][][]) {
            for (int i = 0; i < threadCount; i++) {
                permanentDensityLoop[i].setPermanent(globalMultipoles);
            }
        }

        @Override
        public void run() {
            int ti = getThreadIndex();
            int work1 = nWork - 1;
            PermanentDensityLoop thisLoop = permanentDensityLoop[ti];
            try {
                execute(0, nfftTotal - 1, gridInitLoop);
                execute(0, work1, thisLoop.setOctant(0));
                // Fractional chunks along the C-axis.
                if (nC > 1) {
                    execute(0, work1, thisLoop.setOctant(1));
                    // Fractional chunks along the B-axis.
                    if (nB > 1) {
                        execute(0, work1, thisLoop.setOctant(2));
                        execute(0, work1, thisLoop.setOctant(3));
                        // Fractional chunks along the A-axis.
                        if (nA > 1) {
                            execute(0, work1, thisLoop.setOctant(4));
                            execute(0, work1, thisLoop.setOctant(5));
                            execute(0, work1, thisLoop.setOctant(6));
                            execute(0, work1, thisLoop.setOctant(7));
                        }
                    }
                }
            } catch (Exception e) {
                logger.severe(e.toString());
            }
        }

        private class GridInitLoop extends IntegerForLoop {

            private final IntegerSchedule schedule = IntegerSchedule.fixed();

            @Override
            public IntegerSchedule schedule() {
                return schedule;
            }

            @Override
            public void run(int lb, int ub) {
                for (int i = lb; i <= ub; i++) {
                    densityGrid[i] = 0.0;
                }
            }
        }

        private class PermanentDensityLoop extends IntegerForLoop {

            private int octant = 0;
            private double globalMultipoles[][][] = null;
            private final IntegerSchedule schedule = IntegerSchedule.fixed();
            // 128 bytes of extra padding to avert cache interference.
            private long p0, p1, p2, p3, p4, p5, p6, p7;
            private long p8, p9, pa, pb, pc, pd, pe, pf;

            public void setPermanent(double globalMultipoles[][][]) {
                this.globalMultipoles = globalMultipoles;
            }

            public PermanentDensityLoop setOctant(int octant) {
                this.octant = octant;
                return this;
            }

            @Override
            public IntegerSchedule schedule() {
                return schedule;
            }

            @Override
            public void run(int lb, int ub) {
                // Loop over work cells
                for (int icell = lb; icell <= ub; icell++) {
                    int ia = workA[icell];
                    int ib = workB[icell];
                    int ic = workC[icell];
                    switch (octant) {
                        // Case 0 -> In place.
                        case 0:
                            gridCell(ia, ib, ic);
                            break;
                        // Case 1: Step along the C-axis.
                        case 1:
                            gridCell(ia, ib, ic + 1);
                            break;
                        // Case 2 & 3: Step along the B-axis.
                        case 2:
                            gridCell(ia, ib + 1, ic);
                            break;
                        case 3:
                            gridCell(ia, ib + 1, ic + 1);
                            break;
                        // Case 4-7: Step along the A-axis.
                        case 4:
                            gridCell(ia + 1, ib, ic);
                            break;
                        case 5:
                            gridCell(ia + 1, ib, ic + 1);
                            break;
                        case 6:
                            gridCell(ia + 1, ib + 1, ic);
                            break;
                        case 7:
                            gridCell(ia + 1, ib + 1, ic + 1);
                            break;
                        default:
                            String message = "Programming error in PermanentDensityLoop.\n";
                            logger.severe(message);
                            System.exit(-1);
                    }
                }
            }

            private void gridCell(int ia, int ib, int ic) {
                if (globalMultipoles != null) {
                    for (int iSymm = 0; iSymm < nSymm; iSymm++) {
                        final int pairList[] = cellList[iSymm];
                        final int index = ia + ib * nA + ic * nAB;
                        final int start = cellStart[iSymm][index];
                        final int stop = start + cellCount[iSymm][index];
                        for (int i = start; i < stop; i++) {
                            int n = pairList[i];
                            gridPermanent(iSymm, n);
                        }
                    }
                }
            }

            private void gridPermanent(int iSymm, int n) {
                final double gm[] = globalMultipoles[iSymm][n];
                final double fm[] = fractionalMultipole[iSymm][n];
                // charge
                fm[0] = gm[0];
                // dipole
                for (int j = 1; j < 4; j++) {
                    fm[j] = 0.0;
                    for (int k = 1; k < 4; k++) {
                        fm[j] = fm[j] + tmm[j][k] * gm[k];
                    }
                }
                // quadrupole
                for (int j = 4; j < 10; j++) {
                    fm[j] = 0.0;
                    for (int k = 4; k < 7; k++) {
                        fm[j] = fm[j] + tmm[j][k] * gm[k];
                    }
                    for (int k = 7; k < 10; k++) {
                        fm[j] = fm[j] + tmm[j][k] * 2.0 * gm[k];
                    }
                    /**
                     * Fractional quadrupole components are pre-multiplied by a
                     * factor of 1/3 that arises in their potential.
                     */
                    fm[j] = fm[j] / 3.0;
                }
                final double[][] splx = splineX[iSymm][n];
                final double[][] sply = splineY[iSymm][n];
                final double[][] splz = splineZ[iSymm][n];
                final int igrd0 = initGrid[iSymm][n][0];
                final int jgrd0 = initGrid[iSymm][n][1];
                int k0 = initGrid[iSymm][n][2];
                final double c = fm[t000];
                final double dx = fm[t100];
                final double dy = fm[t010];
                final double dz = fm[t001];
                final double qxx = fm[t200];
                final double qyy = fm[t020];
                final double qzz = fm[t002];
                final double qxy = fm[t110];
                final double qxz = fm[t101];
                final double qyz = fm[t011];
                for (int ith3 = 0; ith3 < bSplineOrder; ith3++) {
                    final double splzi[] = splz[ith3];
                    final double v0 = splzi[0];
                    final double v1 = splzi[1];
                    final double v2 = splzi[2];
                    final double c0 = c * v0;
                    final double dx0 = dx * v0;
                    final double dy0 = dy * v0;
                    final double dz1 = dz * v1;
                    final double qxx0 = qxx * v0;
                    final double qyy0 = qyy * v0;
                    final double qzz2 = qzz * v2;
                    final double qxy0 = qxy * v0;
                    final double qxz1 = qxz * v1;
                    final double qyz1 = qyz * v1;
                    k0++;
                    final int k = k0 + (1 - ((int) signum(k0 + signum_eps))) * fftZ / 2;
                    final int kk = k * xySlice;
                    int j0 = jgrd0;
                    for (int ith2 = 0; ith2 < bSplineOrder; ith2++) {
                        final double splyi[] = sply[ith2];
                        final double u0 = splyi[0];
                        final double u1 = splyi[1];
                        final double u2 = splyi[2];
                        final double term0 = (c0 + dz1 + qzz2) * u0 + (dy0 + qyz1) * u1 + qyy0 * u2;
                        final double term1 = (dx0 + qxz1) * u0 + qxy0 * u1;
                        final double term2 = qxx0 * u0;
                        j0++;
                        final int j = j0 + (1 - ((int) signum(j0 + signum_eps))) * fftY / 2;
                        final int jj = j * (fftX + 2) * 2;
                        final int jk = jj + kk;
                        int i0 = igrd0;
                        for (int ith1 = 0; ith1 < bSplineOrder; ith1++) {
                            i0++;
                            final int i = i0 + (1 - ((int) signum(i0 + signum_eps))) * fftX / 2;
                            // final int ii = i * 2 + jk;
                            int ii = i + jk / 2;
                            final double splxi[] = splx[ith1];
                            final double dq = splxi[0] * term0 + splxi[1] * term1 + splxi[2] * term2;
                            densityGrid[ii] += dq;
                        }
                    }
                }
            }
        }
    }

    private class PermanentReciprocalSumRegion extends ParallelRegion {

        private final ReciprocalSumLoop reciprocalSumLoop;
        private final double permanentFac[];

        public PermanentReciprocalSumRegion() {
            reciprocalSumLoop = new ReciprocalSumLoop();
            permanentFac = new double[nfftTotal / 2];
            lattice();
        }

        public double[] getRecip() {
            return permanentFac;
        }

        @Override
        public void run() {
            try {
                execute(0, nfftTotal / 2 - 1, reciprocalSumLoop);
            } catch (Exception e) {
                logger.severe(e.toString());
            }
        }

        private class ReciprocalSumLoop extends IntegerForLoop {

            private int i, ii;
            private final IntegerSchedule schedule = IntegerSchedule.fixed();
            // 128 bytes of extra padding to avert cache interference.
            private long p0, p1, p2, p3, p4, p5, p6, p7;
            private long p8, p9, pa, pb, pc, pd, pe, pf;

            @Override
            public IntegerSchedule schedule() {
                return schedule;
            }

            @Override
            public void run(int lb, int ub) {
                ii = 2 * lb;
                for (i = lb; i <= ub; i++) {
                    densityGrid[ii++] *= permanentFac[i];
                    densityGrid[ii++] *= permanentFac[i];
                }
            }
        }

        private void lattice() {
            int length = permanentFac.length;
            for (int i = 0; i < length; i++) {
                permanentFac[i] = 0.0;
            }
            int maxfft = fftX;
            if (fftY > maxfft) {
                maxfft = fftY;
            }
            if (fftZ > maxfft) {
                maxfft = fftZ;
            }
            double bsModX[] = new double[fftX];
            double bsModY[] = new double[fftY];
            double bsModZ[] = new double[fftZ];
            double bsarray[] = new double[maxfft];
            double c[] = new double[bSplineOrder];
            bSpline(0.0, bSplineOrder, c);
            for (int i = 1; i < bSplineOrder + 1; i++) {
                bsarray[i] = c[i - 1];
            }
            discreteFTMod(bsModX, bsarray, fftX, bSplineOrder);
            discreteFTMod(bsModY, bsarray, fftY, bSplineOrder);
            discreteFTMod(bsModZ, bsarray, fftZ, bSplineOrder);
            permanentFac[0] = 0.0;
            double r00 = crystal.recip[0][0];
            double r01 = crystal.recip[0][1];
            double r02 = crystal.recip[0][2];
            double r10 = crystal.recip[1][0];
            double r11 = crystal.recip[1][1];
            double r12 = crystal.recip[1][2];
            double r20 = crystal.recip[2][0];
            double r21 = crystal.recip[2][1];
            double r22 = crystal.recip[2][2];
            int ntot = (halfFFTX + 1) * fftY * fftZ;
            double pterm = (PI / aewald) * (PI / aewald);
            double volterm = PI * crystal.volume;
            int nff = (halfFFTX + 1) * fftY;
            int nf1 = (fftX + 1) / 2;
            int nf2 = (fftY + 1) / 2;
            int nf3 = (fftZ + 1) / 2;
            for (int i = 0; i < ntot - 1; i++) {
                int k3 = (i + 1) / nff;
                int j = i - k3 * nff + 1;
                int k2 = j / (halfFFTX + 1);
                int k1 = j - k2 * (halfFFTX + 1);
                int m1 = k1;
                int m2 = k2;
                int m3 = k3;
                if (k1 + 1 > nf1) {
                    m1 -= fftX;
                }
                if (k2 + 1 > nf2) {
                    m2 -= fftY;
                }
                if (k3 + 1 > nf3) {
                    m3 -= fftZ;
                }
                double s1 = r00 * m1 + r01 * m2 + r02 * m3;
                double s2 = r10 * m1 + r11 * m2 + r12 * m3;
                double s3 = r20 * m1 + r21 * m2 + r22 * m3;
                double ssq = s1 * s1 + s2 * s2 + s3 * s3;
                double term = -pterm * ssq;
                double expterm = 0.0;
                if (term > -50.0) {
                    double denom = ssq * volterm * bsModX[k1] * bsModY[k2] * bsModZ[k3];
                    expterm = exp(term) / denom;
                    // if (.not. use_bounds) then
                    // expterm = expterm * (1.0d0-cos(pi*xbox*sqrt(hsq)));
                }
                permanentFac[k3 + k1 * fftZ + k2 * (halfFFTX + 1) * fftZ] = expterm;
            }
        }
    }

    private class PermanentPhiRegion extends ParallelRegion {

        private final FractionalPhiLoop fractionalPhiLoop[];
        private final double splineX[][][][];
        private final double splineY[][][][];
        private final double splineZ[][][][];
        private final int initgrid[][][];

        public PermanentPhiRegion(BSplineRegion bSplineRegion) {
            this.initgrid = bSplineRegion.initGrid;
            this.splineX = bSplineRegion.splineX;
            this.splineY = bSplineRegion.splineY;
            this.splineZ = bSplineRegion.splineZ;
            fractionalPhiLoop = new FractionalPhiLoop[threadCount];
            for (int i = 0; i < threadCount; i++) {
                fractionalPhiLoop[i] = new FractionalPhiLoop();
            }
        }

        @Override
        public void run() {
            try {
                execute(0, nAtoms - 1, fractionalPhiLoop[getThreadIndex()]);
            } catch (Exception e) {
                logger.severe(e.toString());
                e.printStackTrace();
            }
        }

        private class FractionalPhiLoop extends IntegerForLoop {

            private final IntegerSchedule schedule = IntegerSchedule.fixed();

            @Override
            public IntegerSchedule schedule() {
                return schedule;
            }

            @Override
            public void run(final int lb, final int ub) {
                for (int n = lb; n <= ub; n++) {
                    final double[][] splx = splineX[0][n];
                    final double[][] sply = splineY[0][n];
                    final double[][] splz = splineZ[0][n];
                    final int igrd[] = initgrid[0][n];
                    final int igrd0 = igrd[0];
                    final int jgrd0 = igrd[1];
                    int k0 = igrd[2];
                    double tuv000 = 0.0;
                    double tuv100 = 0.0;
                    double tuv010 = 0.0;
                    double tuv001 = 0.0;
                    double tuv200 = 0.0;
                    double tuv020 = 0.0;
                    double tuv002 = 0.0;
                    double tuv110 = 0.0;
                    double tuv101 = 0.0;
                    double tuv011 = 0.0;
                    double tuv300 = 0.0;
                    double tuv030 = 0.0;
                    double tuv003 = 0.0;
                    double tuv210 = 0.0;
                    double tuv201 = 0.0;
                    double tuv120 = 0.0;
                    double tuv021 = 0.0;
                    double tuv102 = 0.0;
                    double tuv012 = 0.0;
                    double tuv111 = 0.0;
                    for (int ith3 = 0; ith3 < bSplineOrder; ith3++) {
                        k0++;
                        final int k = k0 + (1 - ((int) signum(k0 + signum_eps))) * halfFFTZ;
                        final int kk = k * xySlice;
                        int j0 = jgrd0;
                        double tu00 = 0.0;
                        double tu10 = 0.0;
                        double tu01 = 0.0;
                        double tu20 = 0.0;
                        double tu11 = 0.0;
                        double tu02 = 0.0;
                        double tu30 = 0.0;
                        double tu21 = 0.0;
                        double tu12 = 0.0;
                        double tu03 = 0.0;
                        for (int ith2 = 0; ith2 < bSplineOrder; ith2++) {
                            j0++;
                            final int j = j0 + (1 - ((int) signum(j0 + signum_eps))) * halfFFTY;
                            final int jj = j * xSide;
                            final int jk = (jj + kk) / 2;
                            int i0 = igrd0;
                            double t0 = 0.0;
                            double t1 = 0.0;
                            double t2 = 0.0;
                            double t3 = 0.0;
                            for (int ith1 = 0; ith1 < bSplineOrder; ith1++) {
                                i0++;
                                final int i = i0 + (1 - ((int) signum(i0 + signum_eps))) * halfFFTX;
                                final int ii = i + jk;
                                final double tq = densityGrid[ii];
                                final double splxi[] = splx[ith1];
                                t0 += tq * splxi[0];
                                t1 += tq * splxi[1];
                                t2 += tq * splxi[2];
                                t3 += tq * splxi[3];
                            }
                            final double splyi[] = sply[ith2];
                            final double u0 = splyi[0];
                            final double u1 = splyi[1];
                            final double u2 = splyi[2];
                            final double u3 = splyi[3];
                            tu00 += t0 * u0;
                            tu10 += t1 * u0;
                            tu01 += t0 * u1;
                            tu20 += t2 * u0;
                            tu11 += t1 * u1;
                            tu02 += t0 * u2;
                            tu30 += t3 * u0;
                            tu21 += t2 * u1;
                            tu12 += t1 * u2;
                            tu03 += t0 * u3;
                        }
                        final double splzi[] = splz[ith3];
                        final double v0 = splzi[0];
                        final double v1 = splzi[1];
                        final double v2 = splzi[2];
                        final double v3 = splzi[3];
                        tuv000 += tu00 * v0;
                        tuv100 += tu10 * v0;
                        tuv010 += tu01 * v0;
                        tuv001 += tu00 * v1;
                        tuv200 += tu20 * v0;
                        tuv020 += tu02 * v0;
                        tuv002 += tu00 * v2;
                        tuv110 += tu11 * v0;
                        tuv101 += tu10 * v1;
                        tuv011 += tu01 * v1;
                        tuv300 += tu30 * v0;
                        tuv030 += tu03 * v0;
                        tuv003 += tu00 * v3;
                        tuv210 += tu21 * v0;
                        tuv201 += tu20 * v1;
                        tuv120 += tu12 * v0;
                        tuv021 += tu02 * v1;
                        tuv102 += tu10 * v2;
                        tuv012 += tu01 * v2;
                        tuv111 += tu11 * v1;
                    }
                    final double out[] = fractionalMultipolePhi[n];
                    out[t000] = tuv000;
                    out[t100] = tuv100;
                    out[t010] = tuv010;
                    out[t001] = tuv001;
                    out[t200] = tuv200;
                    out[t020] = tuv020;
                    out[t002] = tuv002;
                    out[t110] = tuv110;
                    out[t101] = tuv101;
                    out[t011] = tuv011;
                    out[t300] = tuv300;
                    out[t030] = tuv030;
                    out[t003] = tuv003;
                    out[t210] = tuv210;
                    out[t201] = tuv201;
                    out[t120] = tuv120;
                    out[t021] = tuv021;
                    out[t102] = tuv102;
                    out[t012] = tuv012;
                    out[t111] = tuv111;
                }
            }
        }
    }

    private class PolarizationDensityRegion extends ParallelRegion {

        private final GridInitLoop initLoop;
        private final PolarizationDensityLoop polarizationDensityLoop[];
        private final double splineX[][][][];
        private final double splineY[][][][];
        private final double splineZ[][][][];
        private final int initgrid[][][];

        public PolarizationDensityRegion(BSplineRegion bSplineRegion) {
            this.initgrid = bSplineRegion.initGrid;
            this.splineX = bSplineRegion.splineX;
            this.splineY = bSplineRegion.splineY;
            this.splineZ = bSplineRegion.splineZ;
            initLoop = new GridInitLoop();
            polarizationDensityLoop = new PolarizationDensityLoop[threadCount];
            for (int i = 0; i < threadCount; i++) {
                polarizationDensityLoop[i] = new PolarizationDensityLoop();
            }
        }

        public void setPolarization(double inducedDipole[][][],
                double inducedDipolep[][][]) {
            for (int i = 0; i < threadCount; i++) {
                polarizationDensityLoop[i].setPolarization(inducedDipole,
                        inducedDipolep);
            }
        }

        public void run() {
            int ti = getThreadIndex();
            PolarizationDensityLoop thisLoop = polarizationDensityLoop[ti];
            int work1 = nWork - 1;
            try {
                // Zero out the grid.
                execute(0, polarizationTotal * 2 - 1, initLoop);
                execute(0, work1, thisLoop.setOctant(0));
                // Fractional chunks along the C-axis.
                if (nC > 1) {
                    execute(0, work1, thisLoop.setOctant(1));
                    // Fractional chunks along the B-axis.
                    if (nB > 1) {
                        execute(0, work1, thisLoop.setOctant(2));
                        execute(0, work1, thisLoop.setOctant(3));
                        // Fractional chunks along the A-axis.
                        if (nA > 1) {
                            execute(0, work1, thisLoop.setOctant(4));
                            execute(0, work1, thisLoop.setOctant(5));
                            execute(0, work1, thisLoop.setOctant(6));
                            execute(0, work1, thisLoop.setOctant(7));
                        }
                    }
                }
            } catch (Exception e) {
                logger.severe(e.toString());
            }
        }

        private class GridInitLoop extends IntegerForLoop {

            private final IntegerSchedule schedule = IntegerSchedule.fixed();

            @Override
            public IntegerSchedule schedule() {
                return schedule;
            }

            public void run(int lb, int ub) {
                for (int i = lb; i <= ub; i++) {
                    densityGrid[i] = 0.0;
                }
            }
        }

        private class PolarizationDensityLoop extends IntegerForLoop {

            private int octant = 0;
            private double inducedDipole[][][] = null;
            private double inducedDipolep[][][] = null;
            private final IntegerSchedule schedule = IntegerSchedule.dynamic(1);

            public void setPolarization(double inducedDipole[][][],
                    double inducedDipolep[][][]) {
                this.inducedDipole = inducedDipole;
                this.inducedDipolep = inducedDipolep;
            }

            public PolarizationDensityLoop setOctant(int octant) {
                this.octant = octant;
                return this;
            }

            @Override
            public IntegerSchedule schedule() {
                return schedule;
            }

            @Override
            public void run(int lb, int ub) {
                // Loop over work cells
                for (int icell = lb; icell <= ub; icell++) {
                    int ia = workA[icell];
                    int ib = workB[icell];
                    int ic = workC[icell];
                    switch (octant) {
                        // Case 0 -> In place.
                        case 0:
                            gridCell(ia, ib, ic);
                            break;
                        // Case 1: Step along the C-axis.
                        case 1:
                            gridCell(ia, ib, ic + 1);
                            break;
                        // Case 2 & 3: Step along the B-axis.
                        case 2:
                            gridCell(ia, ib + 1, ic);
                            break;
                        case 3:
                            gridCell(ia, ib + 1, ic + 1);
                            break;
                        // Case 4-7: Step along the A-axis.
                        case 4:
                            gridCell(ia + 1, ib, ic);
                            break;
                        case 5:
                            gridCell(ia + 1, ib, ic + 1);
                            break;
                        case 6:
                            gridCell(ia + 1, ib + 1, ic);
                            break;
                        case 7:
                            gridCell(ia + 1, ib + 1, ic + 1);
                            break;
                        default:
                            String message = "Programming error in PolarizationDensityLoop";
                            logger.severe(message);
                            System.exit(-1);
                    }
                }
            }

            private void gridCell(int ia, int ib, int ic) {
                if (inducedDipole != null) {
                    for (int iSymm = 0; iSymm < nSymm; iSymm++) {
                        final int pairList[] = cellList[iSymm];
                        final int index = ia + ib * nA + ic * nAB;
                        final int start = cellStart[iSymm][index];
                        final int stop = start + cellCount[iSymm][index];
                        for (int i = start; i < stop; i++) {
                            int n = pairList[i];
                            gridPolarization(iSymm, n);
                        }
                    }
                }
            }

            private void gridPolarization(int iSymm, int n) {
                double ind[] = inducedDipole[iSymm][n];
                final double find[] = fractionalDipole[iSymm][n];
                find[0] = a[0][0] * ind[0] + a[0][1] * ind[1] + a[0][2] * ind[2];
                find[1] = a[1][0] * ind[0] + a[1][1] * ind[1] + a[1][2] * ind[2];
                find[2] = a[2][0] * ind[0] + a[2][1] * ind[1] + a[2][2] * ind[2];
                double inp[] = inducedDipolep[iSymm][n];
                final double finp[] = fractionalDipolep[iSymm][n];
                finp[0] = a[0][0] * inp[0] + a[0][1] * inp[1] + a[0][2] * inp[2];
                finp[1] = a[1][0] * inp[0] + a[1][1] * inp[1] + a[1][2] * inp[2];
                finp[2] = a[2][0] * inp[0] + a[2][1] * inp[1] + a[2][2] * inp[2];
                final double[][] splx = splineX[iSymm][n];
                final double[][] sply = splineY[iSymm][n];
                final double[][] splz = splineZ[iSymm][n];
                final int igrd0 = initgrid[iSymm][n][0];
                final int jgrd0 = initgrid[iSymm][n][1];
                int k0 = initgrid[iSymm][n][2];
                final double ux = find[0];
                final double uy = find[1];
                final double uz = find[2];
                final double px = finp[0];
                final double py = finp[1];
                final double pz = finp[2];
                for (int ith3 = 0; ith3 < bSplineOrder; ith3++) {
                    final double splzi[] = splz[ith3];
                    final double v0 = splzi[0];
                    final double v1 = splzi[1];
                    final double dx0 = ux * v0;
                    final double dy0 = uy * v0;
                    final double dz1 = uz * v1;
                    final double px0 = px * v0;
                    final double py0 = py * v0;
                    final double pz1 = pz * v1;
                    k0++;
                    final int k = k0 + (1 - ((int) signum(k0 + signum_eps))) * fftZ / 2;
                    final int kk = k * zSlicep;
                    int j0 = jgrd0;
                    for (int ith2 = 0; ith2 < bSplineOrder; ith2++) {
                        final double splyi[] = sply[ith2];
                        final double u0 = splyi[0];
                        final double u1 = splyi[1];
                        final double term0 = dz1 * u0 + dy0 * u1;
                        final double term1 = dx0 * u0;
                        final double termp0 = pz1 * u0 + py0 * u1;
                        final double termp1 = px0 * u0;
                        j0++;
                        final int j = j0 + (1 - ((int) signum(j0 + signum_eps))) * fftY / 2;
                        final int jj = j * fftX * 2;
                        final int jk = jj + kk;
                        int i0 = igrd0;
                        for (int ith1 = 0; ith1 < bSplineOrder; ith1++) {
                            i0++;
                            final int i = i0 + (1 - ((int) signum(i0 + signum_eps))) * fftX / 2;
                            int ii = 2 * i + jk;
                            final double splxi[] = splx[ith1];
                            final double dq = splxi[0] * term0 + splxi[1] * term1;
                            final double pq = splxi[0] * termp0 + splxi[1] * termp1;
                            densityGrid[ii] += dq;
                            densityGrid[ii + 1] += pq;
                        }
                    }
                }
            }
        }
    }

    private class PolarizationReciprocalSumRegion extends ParallelRegion {

        private final ReciprocalSumLoop reciprocalSumLoop;
        private final double polarizationFac[];

        public PolarizationReciprocalSumRegion() {
            reciprocalSumLoop = new ReciprocalSumLoop();
            polarizationFac = new double[polarizationTotal];
            lattice();
        }

        @Override
        public void run() {
            try {
                execute(0, polarizationTotal - 1, reciprocalSumLoop);
            } catch (Exception e) {
                logger.severe(e.toString());
            }
        }

        public double[] getRecip() {
            return this.polarizationFac;
        }

        private class ReciprocalSumLoop extends IntegerForLoop {

            private final IntegerSchedule schedule = IntegerSchedule.fixed();

            @Override
            public IntegerSchedule schedule() {
                return schedule;
            }

            public void run(int lb, int ub) {
                int ii = 2 * lb;
                for (int i = lb; i <= ub; i++) {
                    densityGrid[ii++] *= polarizationFac[i];
                    densityGrid[ii++] *= polarizationFac[i];
                }
            }
        }

        private void lattice() {
            int maxfft = fftX;
            if (fftY > maxfft) {
                maxfft = fftY;
            }
            if (fftZ > maxfft) {
                maxfft = fftZ;
            }
            double bsModX[] = new double[fftX];
            double bsModY[] = new double[fftY];
            double bsModZ[] = new double[fftZ];
            double bsarray[] = new double[maxfft];
            double c[] = new double[bSplineOrder];
            bSpline(0.0, bSplineOrder, c);
            for (int i = 1; i < bSplineOrder + 1; i++) {
                bsarray[i] = c[i - 1];
            }
            discreteFTMod(bsModX, bsarray, fftX, bSplineOrder);
            discreteFTMod(bsModY, bsarray, fftY, bSplineOrder);
            discreteFTMod(bsModZ, bsarray, fftZ, bSplineOrder);
            polarizationFac[0] = 0.0;
            double r00 = crystal.recip[0][0];
            double r01 = crystal.recip[0][1];
            double r02 = crystal.recip[0][2];
            double r10 = crystal.recip[1][0];
            double r11 = crystal.recip[1][1];
            double r12 = crystal.recip[1][2];
            double r20 = crystal.recip[2][0];
            double r21 = crystal.recip[2][1];
            double r22 = crystal.recip[2][2];
            int ntot = fftX * fftY * fftZ;
            double pterm = (PI / aewald) * (PI / aewald);
            double volterm = PI * crystal.volume;
            int nff = fftX * fftY;
            int nf1 = (fftX + 1) / 2;
            int nf2 = (fftY + 1) / 2;
            int nf3 = (fftZ + 1) / 2;
            for (int i = 0; i < ntot - 1; i++) {
                int k3 = (i + 1) / nff;
                int j = i - k3 * nff + 1;
                int k2 = j / fftX;
                int k1 = j - k2 * fftX;
                int m1 = k1;
                int m2 = k2;
                int m3 = k3;
                if (k1 + 1 > nf1) {
                    m1 -= fftX;
                }
                if (k2 + 1 > nf2) {
                    m2 -= fftY;
                }
                if (k3 + 1 > nf3) {
                    m3 -= fftZ;
                }
                double s1 = r00 * m1 + r01 * m2 + r02 * m3;
                double s2 = r10 * m1 + r11 * m2 + r12 * m3;
                double s3 = r20 * m1 + r21 * m2 + r22 * m3;
                double ssq = s1 * s1 + s2 * s2 + s3 * s3;
                double term = -pterm * ssq;
                double expterm = 0.0;
                if (term > -50.0) {
                    double denom = ssq * volterm * bsModX[k1] * bsModY[k2] * bsModZ[k3];
                    expterm = exp(term) / denom;
                    // if (.not. use_bounds) then
                    // expterm = expterm * (1.0d0-cos(pi*xbox*sqrt(hsq)));
                }
                // polarizationFac[k1 + k2 * nfftX + k3 * nfftX * nfftY] =
                // expterm;
                polarizationFac[k1 * fftZ + k2 * fftX * fftZ + k3] = expterm;
            }
        }
    }

    private class PolarizationPhiRegion extends ParallelRegion {

        private final PolarizationPhiInducedLoop polarizationPhiInducedLoop;
        private final double splineX[][][][];
        private final double splineY[][][][];
        private final double splineZ[][][][];
        private final int initgrid[][][];

        public PolarizationPhiRegion(BSplineRegion bSplineRegion) {
            this.initgrid = bSplineRegion.initGrid;
            this.splineX = bSplineRegion.splineX;
            this.splineY = bSplineRegion.splineY;
            this.splineZ = bSplineRegion.splineZ;
            polarizationPhiInducedLoop = new PolarizationPhiInducedLoop();
        }

        @Override
        public void run() {
            try {
                execute(0, nAtoms - 1, polarizationPhiInducedLoop);
            } catch (Exception e) {
                logger.severe(e.toString());
            }
        }

        private class PolarizationPhiInducedLoop extends IntegerForLoop {

            private final IntegerSchedule schedule = IntegerSchedule.fixed();

            @Override
            public IntegerSchedule schedule() {
                return schedule;
            }

            public void run(int lb, int ub) {
                for (int n = lb; n <= ub; n++) {
                    final double[][] splx = splineX[0][n];
                    final double[][] sply = splineY[0][n];
                    final double[][] splz = splineZ[0][n];
                    final int igrd[] = initgrid[0][n];
                    final int igrd0 = igrd[0];
                    final int jgrd0 = igrd[1];
                    int k0 = igrd[2];
                    double tuv000 = 0.0;
                    double tuv100 = 0.0;
                    double tuv010 = 0.0;
                    double tuv001 = 0.0;
                    double tuv200 = 0.0;
                    double tuv020 = 0.0;
                    double tuv002 = 0.0;
                    double tuv110 = 0.0;
                    double tuv101 = 0.0;
                    double tuv011 = 0.0;
                    double tuv300 = 0.0;
                    double tuv030 = 0.0;
                    double tuv003 = 0.0;
                    double tuv210 = 0.0;
                    double tuv201 = 0.0;
                    double tuv120 = 0.0;
                    double tuv021 = 0.0;
                    double tuv102 = 0.0;
                    double tuv012 = 0.0;
                    double tuv111 = 0.0;
                    double tuv000p = 0.0;
                    double tuv100p = 0.0;
                    double tuv010p = 0.0;
                    double tuv001p = 0.0;
                    double tuv200p = 0.0;
                    double tuv020p = 0.0;
                    double tuv002p = 0.0;
                    double tuv110p = 0.0;
                    double tuv101p = 0.0;
                    double tuv011p = 0.0;
                    double tuv300p = 0.0;
                    double tuv030p = 0.0;
                    double tuv003p = 0.0;
                    double tuv210p = 0.0;
                    double tuv201p = 0.0;
                    double tuv120p = 0.0;
                    double tuv021p = 0.0;
                    double tuv102p = 0.0;
                    double tuv012p = 0.0;
                    double tuv111p = 0.0;
                    for (int ith3 = 0; ith3 < bSplineOrder; ith3++) {
                        k0++;
                        final int k = (k0 + (1 - ((int) signum(k0 + signum_eps))) * fftZ / 2) * zSlicep;
                        int j0 = jgrd0;
                        double tu00 = 0.0;
                        double tu10 = 0.0;
                        double tu01 = 0.0;
                        double tu20 = 0.0;
                        double tu11 = 0.0;
                        double tu02 = 0.0;
                        double tu30 = 0.0;
                        double tu21 = 0.0;
                        double tu12 = 0.0;
                        double tu03 = 0.0;
                        double tu00p = 0.0;
                        double tu10p = 0.0;
                        double tu01p = 0.0;
                        double tu20p = 0.0;
                        double tu11p = 0.0;
                        double tu02p = 0.0;
                        double tu30p = 0.0;
                        double tu21p = 0.0;
                        double tu12p = 0.0;
                        double tu03p = 0.0;
                        for (int ith2 = 0; ith2 < bSplineOrder; ith2++) {
                            j0++;
                            final int j = (2 * j0 + (1 - ((int) signum(j0 + signum_eps))) * fftY) * fftX;
                            final int jk = j + k;
                            int i0 = igrd0;
                            double t0 = 0.0;
                            double t1 = 0.0;
                            double t2 = 0.0;
                            double t3 = 0.0;
                            double t0p = 0.0;
                            double t1p = 0.0;
                            double t2p = 0.0;
                            double t3p = 0.0;
                            for (int ith1 = 0; ith1 < bSplineOrder; ith1++) {
                                i0++;
                                final int i = 2 * i0 + (1 - ((int) signum(i0 + signum_eps))) * fftX + jk;
                                final double tq = densityGrid[i];
                                final double tp = densityGrid[i + 1];
                                final double splxi[] = splx[ith1];
                                t0 += tq * splxi[0];
                                t1 += tq * splxi[1];
                                t2 += tq * splxi[2];
                                t3 += tq * splxi[3];
                                t0p += tp * splxi[0];
                                t1p += tp * splxi[1];
                                t2p += tp * splxi[2];
                                t3p += tp * splxi[3];
                            }
                            final double splyi[] = sply[ith2];
                            final double u0 = splyi[0];
                            final double u1 = splyi[1];
                            final double u2 = splyi[2];
                            final double u3 = splyi[3];
                            tu00 += t0 * u0;
                            tu10 += t1 * u0;
                            tu01 += t0 * u1;
                            tu20 += t2 * u0;
                            tu11 += t1 * u1;
                            tu02 += t0 * u2;
                            tu30 += t3 * u0;
                            tu21 += t2 * u1;
                            tu12 += t1 * u2;
                            tu03 += t0 * u3;
                            tu00p += t0p * u0;
                            tu10p += t1p * u0;
                            tu01p += t0p * u1;
                            tu20p += t2p * u0;
                            tu11p += t1p * u1;
                            tu02p += t0p * u2;
                            tu30p += t3p * u0;
                            tu21p += t2p * u1;
                            tu12p += t1p * u2;
                            tu03p += t0p * u3;
                        }
                        final double splzi[] = splz[ith3];
                        final double v0 = splzi[0];
                        final double v1 = splzi[1];
                        final double v2 = splzi[2];
                        final double v3 = splzi[3];
                        tuv000 += tu00 * v0;
                        tuv100 += tu10 * v0;
                        tuv010 += tu01 * v0;
                        tuv001 += tu00 * v1;
                        tuv200 += tu20 * v0;
                        tuv020 += tu02 * v0;
                        tuv002 += tu00 * v2;
                        tuv110 += tu11 * v0;
                        tuv101 += tu10 * v1;
                        tuv011 += tu01 * v1;
                        tuv300 += tu30 * v0;
                        tuv030 += tu03 * v0;
                        tuv003 += tu00 * v3;
                        tuv210 += tu21 * v0;
                        tuv201 += tu20 * v1;
                        tuv120 += tu12 * v0;
                        tuv021 += tu02 * v1;
                        tuv102 += tu10 * v2;
                        tuv012 += tu01 * v2;
                        tuv111 += tu11 * v1;
                        tuv000p += tu00p * v0;
                        tuv100p += tu10p * v0;
                        tuv010p += tu01p * v0;
                        tuv001p += tu00p * v1;
                        tuv200p += tu20p * v0;
                        tuv020p += tu02p * v0;
                        tuv002p += tu00p * v2;
                        tuv110p += tu11p * v0;
                        tuv101p += tu10p * v1;
                        tuv011p += tu01p * v1;
                        tuv300p += tu30p * v0;
                        tuv030p += tu03p * v0;
                        tuv003p += tu00p * v3;
                        tuv210p += tu21p * v0;
                        tuv201p += tu20p * v1;
                        tuv120p += tu12p * v0;
                        tuv021p += tu02p * v1;
                        tuv102p += tu10p * v2;
                        tuv012p += tu01p * v2;
                        tuv111p += tu11p * v1;
                    }
                    double out[] = fractionalInducedDipolePhi[n];
                    out[t000] = tuv000;
                    out[t100] = tuv100;
                    out[t010] = tuv010;
                    out[t001] = tuv001;
                    out[t200] = tuv200;
                    out[t020] = tuv020;
                    out[t002] = tuv002;
                    out[t110] = tuv110;
                    out[t101] = tuv101;
                    out[t011] = tuv011;
                    out[t300] = tuv300;
                    out[t030] = tuv030;
                    out[t003] = tuv003;
                    out[t210] = tuv210;
                    out[t201] = tuv201;
                    out[t120] = tuv120;
                    out[t021] = tuv021;
                    out[t102] = tuv102;
                    out[t012] = tuv012;
                    out[t111] = tuv111;
                    out = fractionalInducedDipolepPhi[n];
                    out[t000] = tuv000p;
                    out[t100] = tuv100p;
                    out[t010] = tuv010p;
                    out[t001] = tuv001p;
                    out[t200] = tuv200p;
                    out[t020] = tuv020p;
                    out[t002] = tuv002p;
                    out[t110] = tuv110p;
                    out[t101] = tuv101p;
                    out[t011] = tuv011p;
                    out[t300] = tuv300p;
                    out[t030] = tuv030p;
                    out[t003] = tuv003p;
                    out[t210] = tuv210p;
                    out[t201] = tuv201p;
                    out[t120] = tuv120p;
                    out[t021] = tuv021p;
                    out[t102] = tuv102p;
                    out[t012] = tuv012p;
                    out[t111] = tuv111p;
                }
            }
        }
    }

    private void fractionalToCartesianPhi(double frac[][], double cart[][]) {
        for (int i = 0; i < nAtoms; i++) {
            double in[] = frac[i];
            double out[] = cart[i];
            out[0] = tfm[0][0] * in[0];
            for (int j = 1; j < 4; j++) {
                out[j] = 0.0;
                for (int k = 1; k < 4; k++) {
                    out[j] += tfm[j][k] * in[k];
                }
            }
            for (int j = 4; j < 10; j++) {
                out[j] = 0.0;
                for (int k = 4; k < 10; k++) {
                    out[j] += tfm[j][k] * in[k];
                }
            }
        }
    }

    private void transformMultipoleMatrix(double mpole_xy[][]) {
        for (int i = 0; i < 3; i++) {
            a[0][i] = fftX * crystal.recip[i][0];
            a[1][i] = fftY * crystal.recip[i][1];
            a[2][i] = fftZ * crystal.recip[i][2];
        }
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 10; j++) {
                mpole_xy[i][j] = 0.0;
            }
        }
        // charge
        mpole_xy[0][0] = 1.0;
        // dipole
        for (int i = 1; i < 4; i++) {
            for (int j = 1; j < 4; j++) {
                mpole_xy[i][j] = a[i - 1][j - 1];
            }
        }
        // quadrupole
        for (int i1 = 0; i1 < 3; i1++) {
            int k = qi1[i1];
            for (int i2 = 0; i2 < 6; i2++) {
                int i = qi1[i2];
                int j = qi2[i2];
                mpole_xy[i1 + 4][i2 + 4] = a[k][i] * a[k][j];
            }
        }
        for (int i1 = 3; i1 < 6; i1++) {
            int k = qi1[i1];
            int l = qi2[i1];
            for (int i2 = 0; i2 < 6; i2++) {
                int i = qi1[i2];
                int j = qi2[i2];
                mpole_xy[i1 + 4][i2 + 4] = a[k][i] * a[l][j] + a[k][j] * a[l][i];
            }
        }
    }

    private void transformFieldMatrix(double field_xy[][]) {
        for (int i = 0; i < 3; i++) {
            a[i][0] = fftX * crystal.recip[i][0];
            a[i][1] = fftY * crystal.recip[i][1];
            a[i][2] = fftZ * crystal.recip[i][2];
        }
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 10; j++) {
                field_xy[i][j] = 0.0;
            }
        }
        field_xy[0][0] = 1.0;
        for (int i = 1; i < 4; i++) {
            for (int j = 1; j < 4; j++) {
                field_xy[i][j] = a[i - 1][j - 1];
            }
        }
        for (int i1 = 0; i1 < 3; i1++) {
            int k = qi1[i1];
            for (int i2 = 0; i2 < 3; i2++) {
                int i = qi1[i2];
                field_xy[i1 + 4][i2 + 4] = a[k][i] * a[k][i];
            }
            for (int i2 = 3; i2 < 6; i2++) {
                int i = qi1[i2];
                int j = qi2[i2];
                field_xy[i1 + 4][i2 + 4] = 2.0 * a[k][i] * a[k][j];
            }
        }
        for (int i1 = 3; i1 < 6; i1++) {
            int k = qi1[i1];
            int n = qi2[i1];
            for (int i2 = 0; i2 < 3; i2++) {
                int i = qi1[i2];
                field_xy[i1 + 4][i2 + 4] = a[k][i] * a[n][i];
            }
            for (int i2 = 3; i2 < 6; i2++) {
                int i = qi1[i2];
                int j = qi2[i2];
                field_xy[i1 + 4][i2 + 4] = a[k][i] * a[n][j] + a[n][i] * a[k][j];
            }
        }
    }

    /**
     * Assign asymmetric and symmetry mate atoms to cells. This is very fast;
     * there is little to be gained from parallelizing it at this point.
     */
    private void assignAtomsToCells() {
        // Zero out the cell counts.
        for (int iSymm = 0; iSymm < nSymm; iSymm++) {
            final int cellIndexs[] = cellIndex[iSymm];
            final int cellCounts[] = cellCount[iSymm];
            final int cellStarts[] = cellStart[iSymm];
            final int cellLists[] = cellList[iSymm];
            final int cellOffsets[] = cellOffset[iSymm];
            for (int i = 0; i < nCells; i++) {
                cellCounts[i] = 0;
            }
            // Convert to fractional coordinates.
            final double redi[][] = coordinates[iSymm];
            final double x[] = redi[0];
            final double y[] = redi[1];
            final double z[] = redi[2];
            crystal.toFractionalCoordinates(nAtoms, x, y, z, xf, yf, zf);
            // Assign each atom to a cell using fractional coordinates.
            for (int i = 0; i < nAtoms; i++) {
                double xu = xf[i];
                double yu = yf[i];
                double zu = zf[i];
                // Move the atom into the range 0.0 <= x < 1.0
                while (xu >= 1.0) {
                    xu -= 1.0;
                }
                while (xu < 0.0) {
                    xu += 1.0;
                }
                while (yu >= 1.0) {
                    yu -= 1.0;
                }
                while (yu < 0.0) {
                    yu += 1.0;
                }
                while (zu >= 1.0) {
                    zu -= 1.0;
                }
                while (zu < 0.0) {
                    zu += 1.0;
                }
                // The cell indices of this atom.
                final int a = (int) Math.floor(xu * nA);
                final int b = (int) Math.floor(yu * nB);
                final int c = (int) Math.floor(zu * nC);
                if (iSymm == 0) {
                    cellA[i] = a;
                    cellB[i] = b;
                    cellC[i] = c;
                }
                // The cell index of this atom.
                final int index = a + b * nA + c * nAB;
                cellIndexs[i] = index;
                // The offset of this atom from the beginning of the cell.
                cellOffsets[i] = cellCounts[index]++;
            }
            // Define the starting indices.
            cellStarts[0] = 0;
            for (int i = 1; i < nCells; i++) {
                final int i1 = i - 1;
                cellStarts[i] = cellStarts[i1] + cellCounts[i1];
            }
            // Move atom locations into a list ordered by cell.
            for (int i = 0; i < nAtoms; i++) {
                final int index = cellIndexs[i];
                cellLists[cellStarts[index]++] = i;
            }
            // Redefine the starting indices again.
            cellStarts[0] = 0;
            for (int i = 1; i < nCells; i++) {
                final int i1 = i - 1;
                cellStarts[i] = cellStarts[i1] + cellCounts[i1];
            }
        }
    }

    /**
     * Computes the modulus of the discrete Fourier fft of "bsarray" and stores
     * it in "bsmod".
     *
     * @param bsmod
     * @param bsarray
     * @param nfft
     * @param order
     */
    private static void discreteFTMod(double bsmod[], double bsarray[],
            int nfft, int order) {
        /**
         * Get the modulus of the discrete Fourier fft.
         */
        double factor = 2.0 * PI / nfft;
        for (int i = 0; i < nfft; i++) {
            double sum1 = 0.0;
            double sum2 = 0.0;
            for (int j = 0; j < nfft; j++) {
                double arg = factor * (i * j);
                sum1 = sum1 + bsarray[j] * cos(arg);
                sum2 = sum2 + bsarray[j] * sin(arg);
            }
            bsmod[i] = sum1 * sum1 + sum2 * sum2;
        }
        /**
         * Fix for exponential Euler spline interpolation failure.
         */
        double eps = 1.0e-7;
        if (bsmod[0] < eps) {
            bsmod[0] = 0.5 * bsmod[1];
        }
        for (int i = 1; i < nfft - 1; i++) {
            if (bsmod[i] < eps) {
                bsmod[i] = 0.5 * (bsmod[i - 1] + bsmod[i + 1]);
            }
        }
        if (bsmod[nfft - 1] < eps) {
            bsmod[nfft - 1] = 0.5 * bsmod[nfft - 2];
        }
        /**
         * Compute and apply the optimal zeta coefficient.
         */
        int jcut = 50;
        int order2 = 2 * order;
        for (int i = 0; i < nfft; i++) {
            int k = i;
            double zeta;
            if (i > nfft / 2) {
                k = k - nfft;
            }
            if (k == 0) {
                zeta = 1.0;
            } else {
                double sum1 = 1.0;
                double sum2 = 1.0;
                factor = PI * k / nfft;
                for (int j = 0; j < jcut; j++) {
                    double arg = factor / (factor + PI * (j + 1));
                    sum1 = sum1 + pow(arg, order);
                    sum2 = sum2 + pow(arg, order2);
                }
                for (int j = 0; j < jcut; j++) {
                    double arg = factor / (factor - PI * (j + 1));
                    sum1 = sum1 + pow(arg, order);
                    sum2 = sum2 + pow(arg, order2);
                }
                zeta = sum2 / sum1;
            }
            bsmod[i] = bsmod[i] * zeta * zeta;
        }
    }
    private final double a[][];
    private static final double signum_eps = 0.1;
    private final double tfm[][] = new double[10][10];
    private final double tmm[][] = new double[10][10];
    /**
     * First lookup index to pack a 2D tensor into a 1D array.
     */
    private static final int qi1[] = {0, 1, 2, 0, 0, 1};
    /**
     * Second lookup index to pack a 2D tensor into a 1D array.
     */
    private static final int qi2[] = {0, 1, 2, 1, 2, 2};
    private static final int tensorCount = TensorRecursion.tensorCount(3);
    private static final int t000 = 0;
    private static final int t100 = 1;
    private static final int t010 = 2;
    private static final int t001 = 3;
    private static final int t200 = 4;
    private static final int t020 = 5;
    private static final int t002 = 6;
    private static final int t110 = 7;
    private static final int t101 = 8;
    private static final int t011 = 9;
    private static final int t300 = 10;
    private static final int t030 = 11;
    private static final int t003 = 12;
    private static final int t210 = 13;
    private static final int t201 = 14;
    private static final int t120 = 15;
    private static final int t021 = 16;
    private static final int t102 = 17;
    private static final int t012 = 18;
    private static final int t111 = 19;
}
