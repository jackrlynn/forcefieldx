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
package ffx.ui;

import java.awt.Cursor;
import java.util.logging.Logger;

import org.apache.commons.lang.time.StopWatch;

import ffx.potential.bonded.Utilities;
import ffx.potential.bonded.Utilities.FileType;
import ffx.parsers.SystemFilter;

/**
 * The FileOpener class opens a file into Force Field X using a filter
 * from the ffe.parsers package. The OpenFile class implements the Runnable
 * interface so that opening a file does not freeze FFX.
 */
public class FileOpener
        implements Runnable {

    private static final Logger logger = Logger.getLogger(FileOpener.class.getName());
    
    private static final long KB = 1024;
    private static final long MB = KB * KB;
    SystemFilter systemFilter = null;
    MainPanel mainPanel = null;
    private boolean timer = false;
    private boolean gc = false;
    private long occupiedMemory;
    private StopWatch stopWatch;

    public FileOpener(SystemFilter systemFilter, MainPanel mainPanel) {
        this.systemFilter = systemFilter;
        this.mainPanel = mainPanel;
        if (System.getProperty("ffx.timer", "false").equalsIgnoreCase("true")) {
            timer = true;
            if (System.getProperty("ffx.timer.gc", "false").equalsIgnoreCase(
                    "true")) {
                gc = true;
            }
        }
    }

    private void open() {
        if (timer) {
            startTimer();
        }
        FFXSystem ffxSystem = null;
        // Continue if the file was read in successfully
        if (systemFilter.readFile()) {
            ffxSystem = (FFXSystem) systemFilter.getMolecularSystem();
            if (ffxSystem.getFileType() != FileType.PDB) {
                Utilities.biochemistry(ffxSystem, systemFilter.getAtomList());
            }
            // Add the opened system to the Multiscale Hierarchy
            mainPanel.getHierarchy().addSystemNode(ffxSystem);
        }
        mainPanel.setCursor(Cursor.getDefaultCursor());

        /**
        PMEWisdom pmeWisdom = new PMEWisdom( ffxSystem );
        pmeWisdom.run();

        PotentialEnergy energy = new PotentialEnergy(ffxSystem);
        long time = System.nanoTime();
        double e = energy.energy(true, true);
        time = System.nanoTime() - time;

        for (int j = 0; j < 20; j++) {
            long newTime = System.nanoTime();
            e = energy.energy(true, true);
            newTime = System.nanoTime() - newTime;
            if (newTime < time) {
                time = newTime;
            }
        }
        logger.info(String.format("Best Time: %10.3f (sec)", time * 1.0e-9));

        */

        /**
        XYZFilter xyzFilter = new XYZFilter(ffxSystem);
        xyzFilter.writeP1(energy.getCrystal());
        System.exit(1);
         */
        if (timer) {
            stopTimer(ffxSystem);
        }
    }

    @Override
    public void run() {
        if (mainPanel != null && systemFilter != null) {
            open();
        }
    }

    /**
     * Rather verbose output for timed File Operations makes it easy to grep log
     * files for specific information.
     */
    private void startTimer() {
        stopWatch = new StopWatch();
        Runtime runtime = Runtime.getRuntime();
        if (gc) {
            runtime.runFinalization();
            runtime.gc();
        }
        occupiedMemory = runtime.totalMemory() - runtime.freeMemory();
        stopWatch.start();
    }

    private void stopTimer(FFXSystem ffeSystem) {
        stopWatch.stop();
        logger.info("Opened " + ffeSystem.toString() + " with " + ffeSystem.getAtomList().size() + " atoms.\n" + "File Op Time  (msec): " + stopWatch.getTime());
        Runtime runtime = Runtime.getRuntime();
        if (gc) {
            runtime.runFinalization();
            runtime.gc();
            long moleculeMemory = (runtime.totalMemory() - runtime.freeMemory()) - occupiedMemory;
            logger.info("File Op Memory  (Kb): " + moleculeMemory / KB);
        }
        occupiedMemory = runtime.totalMemory() - runtime.freeMemory();
        logger.info("\nAfter File Op FFX Up-Time       (sec): " + (MainPanel.stopWatch.getTime()) / 1000 + "\nAfter File Op FFX Memory         (Mb): " + occupiedMemory / MB + " " + runtime.freeMemory() / MB + " " + runtime.totalMemory() / MB);
    }
}
