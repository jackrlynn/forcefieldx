
// Open a test system.
//open "test/snare-brunger-02-1n7s.P212121.xyz"

//return;

// Number of molecular dynamics steps
int nSteps = 1000;
// Time step in femtoseconds.
double timeStep = 1.0;
// Frequency to print out thermodynamics information in picoseconds.
double printInterval = 0.001;
// Temperature in degrees Kelvin.
double temperature = 300.0;
// Reset velocities.
boolean initVelocities = true
// Go!
md(nSteps, timeStep, printInterval, temperature, initVelocities);