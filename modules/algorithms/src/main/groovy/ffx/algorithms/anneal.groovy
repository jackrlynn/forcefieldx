// SIMULATED ANNEALING 
import ffx.algorithms.SimulatedAnnealing;

// Name of the file (PDB or XYZ).
String filename = args[0];
if (filename == null) {
   println("\n Usage: ffxc anneal filename");
   return;
}

// High temperature starting point.
double highTemperature = 1000.0;

// Low temperature end point.
double lowTemperature = 10.0;

// Number of annealing steps.
int annealingSteps = 10;

// Number of molecular dynamics steps at each temperature.
int mdSteps = 100;

// Things below this line normally do not need to be changed.
// ===============================================================================================

println("\n Running simulated annealing on " + filename);
open(filename);

SimulatedAnnealing simulatedAnnealing = new SimulatedAnnealing(active, active.getPotentialEnergy(),
		 active.getProperties(), null);
simulatedAnnealing.anneal(highTemperature, lowTemperature, annealingSteps, mdSteps);
