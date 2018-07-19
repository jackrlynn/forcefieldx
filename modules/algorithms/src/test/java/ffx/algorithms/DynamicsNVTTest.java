/**
 * Title: Force Field X.
 * <p>
 * Description: Force Field X - Software for Molecular Biophysics.
 * <p>
 * Copyright: Copyright (c) Michael J. Schnieders 2001-2016.
 * <p>
 * This file is part of Force Field X.
 * <p>
 * Force Field X is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 3 as published by
 * the Free Software Foundation.
 * <p>
 * Force Field X is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * Force Field X; if not, write to the Free Software Foundation, Inc., 59 Temple
 * Place, Suite 330, Boston, MA 02111-1307 USA
 * <p>
 * Linking this library statically or dynamically with other modules is making a
 * combined work based on this library. Thus, the terms and conditions of the
 * GNU General Public License cover the whole combination.
 * <p>
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
package ffx.algorithms;

import java.util.Arrays;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import edu.rit.pj.Comm;

import ffx.algorithms.groovy.Dynamics;

import groovy.lang.Binding;

/**
 * @author Hernan Bernabe
 */
@RunWith(Parameterized.class)
public class DynamicsNVTTest {

    private String info;
    private String filename;
    private double startingTemp;
    private double tempTolerance = 3.0;
    private double endTotalEnergy;
    private double energyTolerance = 0.5;

    private Binding binding;
    private Dynamics dynamics;

    private static final Logger logger = Logger.getLogger(DynamicsNVTTest.class.getName());

    @Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {
                        "Water Box NVT", // info
                        "ffx/algorithms/structures/waterbox_eq.xyz", // filename
                        299.77,             // Starting temperature.
                        -25260.0499         // Final total energy.
                }
        });
    }

    public DynamicsNVTTest(String info, String filename, double startingTemp, double endTotalEnergy) {
        this.info = info;
        this.filename = filename;
        this.startingTemp = startingTemp;
        this.endTotalEnergy = endTotalEnergy;
    }

    @Before
    public void before() {
        binding = new Binding();
        dynamics = new Dynamics();
        dynamics.setBinding(binding);

        // Initialize Parallel Java
        try {
            Comm.world();
        } catch (IllegalStateException ise) {
            try {
                String args[] = new String[0];
                Comm.init(args);
            } catch (Exception e) {
                String message = String.format(" Exception starting up the Parallel Java communication layer.");
                logger.log(Level.WARNING, message, e.toString());
                message = String.format(" Skipping NVT dynamics test.");
                logger.log(Level.WARNING, message, e.toString());
                fail();
            }
        }
    }

    @Test
    public void testDynamicsNVT() {

        // Set-up the input arguments for the script.
        String[] args = {"-n", "10", "-t", "298.15", "-i", "VelocityVerlet", "-b", "Bussi", "-r", "0.001", "src/main/java/" + filename};
        binding.setVariable("args", args);

        // Evaluate the script.
        dynamics.run();

        MolecularDynamics molDyn = dynamics.getMolecularDynamics();

        // Assert that temperature is within tolerance at the end of the dynamics trajectory.
        assertEquals(info + " End temperature for NVT test", startingTemp, molDyn.getTemperature(), tempTolerance);

        // Assert that the end total energy is withing the tolerance at the end of the dynamics trajectory.
        assertEquals(info + " End total energy for NVT test and set random seed", endTotalEnergy, molDyn.getTotalEnergy(), energyTolerance);
    }
}
