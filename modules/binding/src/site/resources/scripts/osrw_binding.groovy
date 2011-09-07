// ORTHOGONAL SPACE RANDOM WALK

// Apache Imports
import org.apache.commons.io.FilenameUtils;

// Force Field X Imports
import ffx.algorithms.MolecularDynamics;
import ffx.algorithms.OSRW;
import ffx.algorithms.Thermostat.Thermostats;
import ffx.potential.ForceFieldEnergy;
import ffx.potential.bonded.Atom;
import ffx.potential.bonded.Polymer;
import ffx.potential.bonded.MSNode;
import ffx.potential.bonded.Residue;
import ffx.numerics.VectorMath;




// Name of the file (PDB or XYZ).
String filename = args[0];
if (filename == null) {
   println("\n Usage: ffxc osrw filename ligandStart ligandStop lambda fiction mass ");
   return;
}

int ligandStart = 1;
if (args.size() > 1) {
    ligandStart = Integer.parseInt(args[1]);
}

int ligandStop = ligandStart;
if (args.size() > 2) {
    ligandStop = Integer.parseInt(args[2]);
}

double initialLambda = 1.0;
if (args.size() > 3) {
    initialLambda = Double.parseDouble(args[3]);
}

double initialFriction = 1.0e-16;
if (args.size() > 4) {
    initialFriction = Double.parseDouble(args[4]);
}

double mass = 1.0e-18;
if (args.size() > 5) {
    mass = Double.parseDouble(args[5]);
}

// Restart File
File dyn = new File(FilenameUtils.removeExtension(filename) + ".dyn"); 
if (!dyn.exists()) {
   dyn = null;
} 

// Number of molecular dynamics steps
int nSteps = 1000000;

// Time step in femtoseconds.
double timeStep = 1.0;

// Frequency to print out thermodynamics information in picoseconds.
double printInterval = 0.01;

// Frequency to save out coordinates in picoseconds.
double saveInterval = 100.0;

// Temperature in degrees Kelvin.
double temperature = 300.0;

// Thermostats [ ADIABATIC, BERENDSEN, BUSSI ]
Thermostats thermostat = Thermostats.BERENDSEN;

// Reset velocities (ignored if a restart file is given)
boolean initVelocities = true;

// Things below this line normally do not need to be changed.
// ===============================================================================================

println("\n Running Orthogonal Space Random Walk on " + filename);

System.setProperty("lambdaterm", "true");
System.setProperty("ligand-start", Integer.toString(ligandStart));
System.setProperty("ligand-stop", Integer.toString(ligandStop));

open(filename);

ForceFieldEnergy energy = active.getPotentialEnergy();
Atom[] atoms = active.getAtomArray();
int n = atoms.length;

// Apply the ligand atom selection
for (int i = ligandStart; i <= ligandStop; i++) {
    Atom ai = atoms[i - 1];
    ai.setApplyLambda(true);
}

//Loop over ligand atoms and non ligand atoms to find the closest two, then create a restraint bond

double dist = 1000000;
Atom a1,a2;

int molsize = 0;

MSNode ligand;
for(MSNode m : active.getMolecules()){
	if(m.getAtomList().size() > molsize){
		molsize = m.getAtomList().size();
		ligand = m;
	}
}

for(Polymer p : active.getChains()){
	for(Residue r : p.getResidues()){
		for (Enumeration e = r.getAtomNode().children(); e.hasMoreElements();) {
     		Atom a_poly = (Atom) e.nextElement();
     		double[] xyz1 = a_poly.getXYZ();
     		for(Atom a : ligand.getAtomList()){
     			double[] xyz2 = a.getXYZ();
     			double d = VectorMath.dist(xyz1,xyz2);
     			if(d < dist){
     				dist = d;
     				a1 = a_poly;
     				a2 = a;
     			}
     		}
		}
	}
}

energy.setRestraintBond(a1,a2,dist);
//System.out.println(a1.xyzIndex+" "+a2.xyzIndex+" "+dist);
//return;



// Turn off checks for overlapping atoms, which is expected for lambda=0.
energy.getCrystal().setSpecialPositionCutoff(0.0);

// Wrap the potential energy inside an OSRW instance.
OSRW osrw = new OSRW(energy, energy, active.getProperties(), atoms, temperature, timeStep);
osrw.setLambda(initialLambda);
osrw.setThetaFrication(initialFriction);
osrw.setThetaMass(mass);

// Create the MolecularDynamics instance.
MolecularDynamics molDyn = new MolecularDynamics(active, osrw, active.getProperties(), null, thermostat);

// Start sampling.
molDyn.dynamic(nSteps, timeStep, printInterval, saveInterval, temperature, initVelocities, dyn);