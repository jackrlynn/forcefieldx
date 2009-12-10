/**
 * <p>Title: Force Field X</p>
 * <p>Description: Force Field X is a Molecular Engineering Environment</p>
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
package ffx.ui.commands;

import java.io.Serializable;

/**
 * The TinkerUpdate class is a serializable wrapper for TINKER simulation data
 * that changes during a simulation.
 */
public class TinkerUpdate implements Serializable {
	private static final long serialVersionUID = 1L;
	public static int NONE = 0;
	public static int SIMULATION = 1;
	public static int OPTIMIZATION = 2;
	public boolean read = true;
	// Type
	public int type;
	// Coordinates
	public int numatoms;
	public double[][] coordinates = null;
	// Simulation Data
	public double time = -0.1d;
	public double temperature = 0.0d;
	public double energy = 0.0d;
	public double potential = 0.0d;
	public double kinetic = 0.0d;
	public double intermolecular = 0.0d;
	public double pressure = 0.0d;
	public double density = 0.0d;
	public double[][] velocity = null;
	public double[][] acceleration = null;
	// Optimization Data
	public int step = 0;
	public double[][] gradients = null;
	// Amoeba Data
	public boolean amoeba;
	public double[][] induced = null;

	public TinkerUpdate(int n, int t, boolean a) {
		numatoms = n;
		amoeba = a;
		type = t;
		coordinates = new double[3][numatoms];
		if (type == SIMULATION) {
			velocity = new double[3][numatoms];
			acceleration = new double[3][numatoms];
		} else if (type == OPTIMIZATION) {
			gradients = new double[3][numatoms];
		}
		if (amoeba) {
			induced = new double[3][numatoms];
		}
	}

	public boolean isNewer(FFXMessage message) {
		if (type == SIMULATION && time > message.getTime()) {
			return true;
		}
		if (type == OPTIMIZATION && step > message.getStep()) {
			return true;
		}
		return false;
	}

	public void print() {
		if (type == TinkerUpdate.SIMULATION) {
			System.out.println("Time: " + time + " Energy: " + energy);
		} else {
			System.out.println("Step: " + step + " Energy: " + energy);
		}
	}
}
