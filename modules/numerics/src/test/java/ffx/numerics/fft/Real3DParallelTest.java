/**
 * <p>Title: Force Field X</p>
 * <p>Description: Force Field X is a Molecular Biophysics Environment</p>
 * <p>Copyright: Copyright (c) Michael J. Schnieders 2002-2009</p>
 *
 * @author Michael J. Schnieders
 * @version 0.1
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
package ffx.numerics.fft;

import edu.rit.pj.ParallelTeam;
import java.util.Arrays;
import java.util.Collection;
import java.util.Random;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import static org.junit.Assert.*;

/**
 *
 * @author schnied
 */
@RunWith(Parameterized.class)
public class Real3DParallelTest {

    @Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {{"Test nx=32, ny=32, nz=32, nCPUs=1}", 32, 32, 32, 1},
                                             {"Test nx=32, ny=32, nz=32, nCPUs=2}", 32, 32, 32, 2},
                                             {"Test nx=32, ny=45, nz=21, nCPUs=1}", 32, 45, 21, 1},
                                             {"Test nx=32, ny=45, nz=21, nCPUs=2}", 32, 45, 21, 2}
                });
    }
    private final String info;
    private final int nx;
    private final int ny;
    private final int nz;
    private final int tot;
    private final int paddedTot;
    private final double data[];
    private final double expected[];
    private final double recip[];
    private final ParallelTeam parallelTeam;
    private final double tolerance = 1.0e-14;

    public Real3DParallelTest(String info, int nx, int ny, int nz, int nCPUs) {
        this.info = info;
        this.nx = nx;
        this.ny = ny;
        this.nz = nz;
        tot = nx * ny * nz;
        paddedTot = (nx + 2) * ny * nz;
        data = new double[paddedTot * 2];
        expected = new double[tot];
        recip = new double[tot];
        parallelTeam = new ParallelTeam(nCPUs);
    }

    @Before
    public void setUp() {
        Random random = new Random();
        int paddedIndex = 0;
        int index = 0;
        for (int z = 0; z < nz; z++) {
            for (int y = 0; y < ny; y++) {
                for (int x = 0; x < nx; x++) {
                    double r = random.nextDouble();
                    data[paddedIndex++] = r;
                    expected[index] = r;
                    recip[index++] = 1.0e0;
                }
                paddedIndex += 2;
            }
        }
    }

    /**
     * Test of the fft and ifft methods, of class Real3DParallel.
     */
    @Test
    public void testFft() {
        Real3DParallel real3D = new Real3DParallel(nx, ny, nz, parallelTeam);
        real3D.fft(data);
        real3D.ifft(data);
        int paddedIndex = 0;
        int index = 0;
        for (int z = 0; z < nz; z++) {
            for (int y = 0; y < ny; y++) {
                for (int x = 0; x < nx; x++) {
                    double actual = data[paddedIndex++] / tot;
                    double orig = expected[index++];
                    assertEquals(info, orig, actual, tolerance);
                }
                paddedIndex += 2;
            }
        }
    }

    /**
     * Test of convolution method, of class Real3DParallel.
     */
    @Test
    public void testConvolution() {
        Real3DParallel real3D = new Real3DParallel(nx, ny, nz, parallelTeam);
        real3D.convolution(data, recip);
        int paddedIndex = 0;
        int index = 0;
        for (int z = 0; z < nz; z++) {
            for (int y = 0; y < ny; y++) {
                for (int x = 0; x < nx; x++) {
                    double actual = data[paddedIndex++] / tot;
                    double orig = expected[index++];
                    assertEquals(info, orig, actual, tolerance);
                }
                paddedIndex += 2;
            }
        }
    }
}
