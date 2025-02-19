package seakers.trussaos;

import org.moeaframework.core.Solution;
import org.moeaframework.core.variable.BinaryVariable;
import org.moeaframework.problem.AbstractProblem;
import com.mathworks.engine.*;
import seakers.trussaos.architecture.TrussRepeatableArchitecture;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.lang.Math.*;

/**
 * The problem class for the Truss Optimization problem. Initial deveolopment is for the 2D 3x3 nodal grid case.
 *
 * @author roshan94
 */

public class TrussAOSProblem extends AbstractProblem {

    private final boolean UseFibreStiffnessModel;

    private final String csvSavePath;

    private final MatlabEngine engine;

    private final double sel;

    private final double sidenum;

    private final double nucFac;

    private final double radius;

    private final double YoungsModulus;

    private final double targetStiffnessRatio;

    private final double[][] NodalPositionArray = new double[9][2];

    public TrussAOSProblem (String savePath, boolean FibreStiffness, double targetCRatio, MatlabEngine eng) {

        super(32,2);

        this.UseFibreStiffnessModel = FibreStiffness;

        this.radius = 0.00005;
        this.YoungsModulus = 10000.0;
        this.sel = 0.05;
        this.sidenum = 3.0;
        this.nucFac = 1.0;
        this.targetStiffnessRatio = targetCRatio;
        this.csvSavePath = savePath;
        this.engine = eng;

        double[][] RelativeNodalPositions = {{0,0},{0,0.5},{0,1},{0.5,0},{0.5,0.5},{0.5,1},{1,0},{1,0.5},{1,1}};
        for (int i = 0; i < RelativeNodalPositions.length; i++){
            for (int j = 0; j < RelativeNodalPositions[0].length; j++){
            this.NodalPositionArray[i][j] = RelativeNodalPositions[i][j] * sel;
            }
        }
    }

    public TrussAOSProblem (String savePath, boolean FibreStiffness, int numVariables, double rad, double sideLength, double E, double targetCRatio, MatlabEngine eng) {

        super(numVariables,2);

        this.UseFibreStiffnessModel = FibreStiffness;

        this.radius = rad;
        this.sel = sideLength;
        this.YoungsModulus = E;
        this.sidenum = 3.0;
        this.nucFac = 1.0;
        this.targetStiffnessRatio = targetCRatio;
        this.csvSavePath = savePath;
        this.engine = eng;

        double[][] RelativeNodalPositions = {{0,0},{0,0.5},{0,1},{0.5,0},{0.5,0.5},{0.5,1},{1,0},{1,0.5},{1,1}};
        for (int i = 0; i < RelativeNodalPositions.length; i++){
            for (int j = 0; j < RelativeNodalPositions[0].length; j++){
                this.NodalPositionArray[i][j] = RelativeNodalPositions[i][j] * sel;
            }
        }
    }

    @Override
    public void evaluate(Solution sltn) {
        TrussRepeatableArchitecture trussArch = new TrussRepeatableArchitecture(sltn);
        int[][] designConnArray = trussArch.getConnectivityArrayFromSolution(sltn);
        //int designTrussCount = trussArch.getTrussCountFromSolution(sltn);
        //System.out.println(Arrays.deepToString(designConnArray));
        //System.out.println(designTrussCount);

        //MatlabEngine eng = null;
        double C11;
        double C22;
        // int C11Integer;
        // int C22Integer;
        double penaltyFactor = 1;
        //try {
            //engine.startMatlab();
        //} catch (EngineException | InterruptedException e) {
            //e.printStackTrace();
        //}
        //MatlabEngine eng = MatlabEngine.startMatlab();
        if (UseFibreStiffnessModel) {
            Object[] outputs = null;
            try {
                //assert eng != null;
                outputs = engine.feval(2,"fiberStiffnessModel",sel,radius,YoungsModulus,designConnArray,sidenum,nucFac);
            } catch (InterruptedException | ExecutionException | NullPointerException e) {
                e.printStackTrace();
            }
            // C11Integer = (int)outputs[0];
            // C22Integer = (int)outputs[1];
            // C11 = (double)C11Integer;
            // C22 = (double)C22Integer;
            C11 = (double)outputs[0];
            C22 = (double)outputs[1];
            penaltyFactor = 1.5;
        }
        else {
            double[][] stiffnessMatrix = new double[3][3];
            double area = Math.PI * radius * radius;
            Object[] outputs = null;
            try {
                outputs = engine.feval(3,"generateC",sel,radius,NodalPositionArray,designConnArray,area,YoungsModulus,stiffnessMatrix);
            } catch (InterruptedException | ExecutionException | NullPointerException e) {
                e.printStackTrace();
            }
            stiffnessMatrix = (double[][])outputs[0];
            C11 = stiffnessMatrix[0][0];
            C22 = stiffnessMatrix[1][1];
            if (Double.isNaN(C11) | Double.isNaN(C22)) {
                C11 = 1e-6;
                C22 = 1e-3;
            }
        }
        double designFeasibilityScore = 0.0;
        double designStabilityScore = 0.0;
        double volFrac = 0.0;
        try {
            designFeasibilityScore = getFeasibilityScore(designConnArray);
        } catch (ExecutionException | InterruptedException | NullPointerException e) {
            e.printStackTrace();
        }
        try {
            designStabilityScore = getStabilityScore(designConnArray);
        } catch (ExecutionException | InterruptedException | NullPointerException e) {
            e.printStackTrace();
        }
        try {
            volFrac = getVolumeFraction(designConnArray);
        } catch (ExecutionException | InterruptedException | NullPointerException e) {
            e.printStackTrace();
        }

        double[] objectives = new double[2];
        double penalty = (Math.log10(Math.abs(designFeasibilityScore)) + Math.log10(Math.abs(designStabilityScore)))/2;
        objectives[0] = Math.abs((C11/C22) - targetStiffnessRatio)/15 - penaltyFactor*penalty;
        objectives[1] = -(C22/volFrac)/8500 - penaltyFactor*penalty;

        sltn.setObjectives(objectives);

        //HashMap<String, Object> constraints = new HashMap<String, Object>();
        //constraints.put("FeasibilityViolation", 1 - designFeasibilityScore);
        //constraints.put("StabilityViolation", 1 - designStabilityScore);
        //sltn.addAttributes(constraints);

        sltn.setAttribute("FeasibilityViolation", 1 - designFeasibilityScore);
        sltn.setAttribute("StabilityViolation", 1 - designStabilityScore);
    }

    public double getFeasibilityScore (int[][] designConnectivityArray) throws ExecutionException, InterruptedException, NullPointerException {
        return (double)engine.feval("feasibility_checker_nonbinary_V2",NodalPositionArray,designConnectivityArray);
    }

    public double getStabilityScore (int[][] designConnectivityArray) throws ExecutionException, InterruptedException, NullPointerException {
        // Object stabilityOutput;
        // stabilityOutput = eng.feval("stabilityTester_2D_V6_1",sidenum,designConnectivityArray,NodalPositionArray,sel);
        // return (double)stabilityOutput;
        return (double)engine.feval("stabilityTester_2D_V7",sidenum,designConnectivityArray,NodalPositionArray,sel);
        // return (double)eng.feval("stabilityTester_2D_updated",sidenum,designConnectivityArray,NodalPositionArray);
    }

    private double getVolumeFraction (int[][] designConnectivityArray) throws ExecutionException, InterruptedException, NullPointerException {
        return (double)engine.feval("calcVF",NodalPositionArray,designConnectivityArray,radius,sel);
    }

    public double[][] getNodalConnectivityArray () {
        return NodalPositionArray;
    }

    @Override
    public Solution newSolution() {
        Solution newSol = new Solution(this.numberOfVariables,2);
        Random rnd = new Random();
        for (int i = 0; i < this.numberOfVariables; i++){
            BinaryVariable newVar = new BinaryVariable(1);
            newVar.set(0,rnd.nextBoolean());
            newSol.setVariable(i,newVar);
        }
        return newSol;
    }
}
