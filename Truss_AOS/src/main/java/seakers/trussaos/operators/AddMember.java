package seakers.trussaos.operators;

import com.mathworks.engine.MatlabEngine;
import org.moeaframework.core.Solution;
import org.moeaframework.core.Variation;
import seakers.trussaos.architecture.TrussRepeatableArchitecture;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class AddMember implements Variation {

    private final boolean keepFeasible;

    private static MatlabEngine engine;

    private final double[][] nodalConnectivityArray;

    public AddMember(boolean keepFeasible, MatlabEngine eng, double[][] nodalConnArray) {
        this.keepFeasible = keepFeasible;
        engine = eng;
        this.nodalConnectivityArray = nodalConnArray;
    }

    @Override
    public int getArity() {return 1;}

    @Override
    public Solution[] evolve(Solution[] sols) {
        TrussRepeatableArchitecture architecture = new TrussRepeatableArchitecture(sols[0]);
        //TrussRepeatableArchitecture architectureCopy = new TrussRepeatableArchitecture(architecture.deepCopy());
        int[][] connectivityArray = architecture.getConnectivityArrayFromSolution(sols[0]);
        TrussRepeatableArchitecture newArchitecture = null;
        if (keepFeasible) {
            boolean feasibilitySameOrBetter = false;
            int numberOfAttempts = 0;
            //ArrayList<ArrayList<Integer>> oldDesignTrussIntersections = new ArrayList<ArrayList<Integer>>();
            //ArrayList<ArrayList<Integer>> newDesignTrussIntersections = new ArrayList<ArrayList<Integer>>();
            int oldDesignTrussIntersections = 0;
            int newDesignTrussIntersections = 0;
            int[][] updatedConnectivityArray;
            while (!feasibilitySameOrBetter && numberOfAttempts < 5) {
                try {
                    oldDesignTrussIntersections = findNumberOfIntersectingTrusses(connectivityArray);
                } catch (ExecutionException | InterruptedException e) {
                    e.printStackTrace();
                }
                int[] nodesWithLowConnections = checkNumberOfConnections(connectivityArray);
                if (nodesWithLowConnections.length == 0) {
                    updatedConnectivityArray = connectivityArray.clone();
                }
                else if (nodesWithLowConnections.length == 1) {
                    Random random = new Random();
                    int[] nodesToConnect = new int[2];
                    nodesToConnect[0] = nodesWithLowConnections[0];
                    int[] secondNodeChoices = new int[8];
                    int nextIndex = 0;
                    for (int i = 0; i < 9; i++) {
                        if (i+1 == nodesWithLowConnections[0])
                            continue;
                        else {
                            secondNodeChoices[nextIndex] = i+1;
                            nextIndex += 1;
                        }
                    }
                    int secondNodeIndex = random.nextInt(secondNodeChoices.length);
                    nodesToConnect[1] = secondNodeChoices[secondNodeIndex];
                    Arrays.sort(nodesToConnect);
                    updatedConnectivityArray = addMemberToConnectivityArray(connectivityArray,nodesToConnect);
                    newArchitecture = architecture.getArchitectureFromConnectivityArray(updatedConnectivityArray);
                }
                else {
                    List<Integer> nodesList = Arrays.stream(nodesWithLowConnections).boxed().collect(Collectors.toList());
                    Collections.shuffle(nodesList);
                    int[] nodesToConnect = new int[2];
                    nodesToConnect[0] = nodesList.get(0);
                    nodesToConnect[1] = nodesList.get(1);
                    Arrays.sort(nodesToConnect);
                    updatedConnectivityArray = addMemberToConnectivityArray(connectivityArray,nodesToConnect);
                }
                try {
                    newDesignTrussIntersections = findNumberOfIntersectingTrusses(updatedConnectivityArray);
                } catch (ExecutionException | InterruptedException e) {
                    e.printStackTrace();
                }
                //int newNumberOfIntersectingTrusses = newDesignTrussIntersections.get(0) == null ? 0 : newDesignTrussIntersections.get(0).size();
                //int oldNumberOfIntersectingTrusses = oldDesignTrussIntersections.get(0) == null ? 0 : oldDesignTrussIntersections.get(0).size();
                if (oldDesignTrussIntersections >= newDesignTrussIntersections) {
                    feasibilitySameOrBetter = true;
                    newArchitecture = architecture.getArchitectureFromConnectivityArray(updatedConnectivityArray);
                }
                else {
                    numberOfAttempts += 1;
                }
            }
        }
        else {
            int[] nodesWithLowConnections = checkNumberOfConnections(connectivityArray);
            if (nodesWithLowConnections.length == 0) {
                newArchitecture = new TrussRepeatableArchitecture(architecture.deepCopy());
            }
            else if (nodesWithLowConnections.length == 1) {
                Random random = new Random();
                int[] nodesToConnect = new int[2];
                nodesToConnect[0] = nodesWithLowConnections[0];
                int[] secondNodeChoices = new int[8];
                int nextIndex = 0;
                for (int i = 0; i < 9; i++) {
                    if (i+1 == nodesWithLowConnections[0])
                        continue;
                    else {
                        secondNodeChoices[nextIndex] = i+1;
                        nextIndex += 1;
                    }
                }
                int secondNodeIndex = random.nextInt(secondNodeChoices.length);
                nodesToConnect[1] = secondNodeChoices[secondNodeIndex];
                Arrays.sort(nodesToConnect);
                int[][] updatedConnectivityArray = addMemberToConnectivityArray(connectivityArray,nodesToConnect);
                newArchitecture = architecture.getArchitectureFromConnectivityArray(updatedConnectivityArray);
            }
            else {
                List<Integer> nodesList = Arrays.stream(nodesWithLowConnections).boxed().collect(Collectors.toList());
                Collections.shuffle(nodesList);
                int[] nodesToConnect = new int[2];
                nodesToConnect[0] = nodesList.get(0);
                nodesToConnect[1] = nodesList.get(1);
                Arrays.sort(nodesToConnect);
                int[][] updatedConnectivityArray = addMemberToConnectivityArray(connectivityArray,nodesToConnect);
                newArchitecture = architecture.getArchitectureFromConnectivityArray(updatedConnectivityArray);
            }
        }
        return new Solution[]{newArchitecture};
    }

    private int findNumberOfIntersectingTrusses (int[][] designConnectivityArray) throws ExecutionException, InterruptedException {
        //int[][] intersectingTrusses = new int[designConnectivityArray.length][2];
        ArrayList<ArrayList<Integer>> intersectingTrusses = new ArrayList<ArrayList<Integer>>();
        double[][] currentLineNodePositions = new double[2][nodalConnectivityArray[0].length];
        double[][] nextLineNodePositions = new double[2][nodalConnectivityArray[0].length];
        int numberOfIntersectingTrussPairs = 0;
        int[] currentTrussPair = new int[2];
        int[] nextTrussPair = new int[2];
        for (int i = 0; i < designConnectivityArray.length - 1; i++) {
            for (int j = i+1; j < designConnectivityArray.length; j++) {
                for (int k = 0; k < 2; k++) {
                    currentTrussPair[k] = designConnectivityArray[i][k];
                    nextTrussPair[k] = designConnectivityArray[j][k];
                    for (int l = 0; l < nodalConnectivityArray[0].length; l++) {
                        currentLineNodePositions[k][l] = nodalConnectivityArray[currentTrussPair[k]-1][l];
                        nextLineNodePositions[k][l] = nodalConnectivityArray[nextTrussPair[k]-1][l];
                    }
                }
                boolean linesIntersect = determineIntersection(currentLineNodePositions,nextLineNodePositions);
                if (linesIntersect) {
                    //intersectingTrusses[numberOfIntersectingTrussPairs][0] = i;
                    //intersectingTrusses[numberOfIntersectingTrussPairs][1] = j;
                    intersectingTrusses.add(new ArrayList<Integer>(Arrays.asList(i,j)));
                    numberOfIntersectingTrussPairs += 1;
                }
            }
        }
        //int[][] trueIntersectingTrusses = new int[numberOfIntersectingTrussPairs][2];
        //for (int i = 0; i < numberOfIntersectingTrussPairs; i++) {
            //for (int j = 0; j < 2; j++) {
                //trueIntersectingTrusses[i][j] = intersectingTrusses[i][j];
            //}
        //}
        return numberOfIntersectingTrussPairs;
    }

    private boolean determineIntersection(double[][] line1, double[][] line2) throws ExecutionException, InterruptedException {
        // line 1 = [p1_x, p1_y; p2_x, p2_y], line 2 = [q1_x, q1_y; q2_x, q2_y]
        double[] p1 = new double[line1[0].length];
        double[] p2 = new double[line1[0].length];
        double[] q1 = new double[line1[0].length];
        double[] q2 = new double[line1[0].length];
        for (int i = 0; i < line1[0].length; i++) {
            p1[i] = line1[0][i];
            p2[i] = line1[1][i];
            q1[i] = line2[0][i];
            q2[i] = line2[1][i];
        }
        //boolean intersects;
        Object obj = null;
        obj = engine.feval("findLineSegIntersection",p1,p2,q1,q2);
        return (boolean)obj;
    }

    private int[][] addMemberToConnectivityArray (int[][] oldConnectivityArray, int[] trussToAdd) {
        int addPosition = 0;
        //boolean positionReached = false;
        for (int i = 0; i < oldConnectivityArray.length; i++) {
            if (oldConnectivityArray[i][0] == trussToAdd[0]) {
                //if (oldConnectivityArray[i][1] < trussToAdd[1])
                    //addPosition += 1;
                if (oldConnectivityArray[i][1] > trussToAdd[1])
                    //positionReached = true;
                    break;
            }
            if (oldConnectivityArray[i][0] > trussToAdd[0])
                break;
            //if (positionReached)
                //break;
            addPosition = i+1;
        }
        int[][] newConnectivityArray = new int[oldConnectivityArray.length + 1][2];
        int currentPosition = 0;
        for (int i = 0; i < newConnectivityArray.length; i++) {
            if (i == addPosition) {
                for (int j = 0; j < 2; j++) {
                    newConnectivityArray[i][j] = trussToAdd[j];
                }
            }
            else {
                for (int j = 0; j < 2; j++) {
                    newConnectivityArray[i][j] = oldConnectivityArray[currentPosition][j];
                }
                currentPosition += 1;
            }
        }
        return newConnectivityArray;
    }

    private boolean[] compareNumberOfConnections (int[] oldConnections, int[] newConnections) {
        int[] oldMidConnections = new int[4];
        int[] oldCornerConnections = new int[4];
        int[] newMidConnections = new int[4];
        int[] newCornerConnections = new int[4];
        System.arraycopy(oldConnections,5,oldMidConnections,0,4);
        System.arraycopy(oldConnections,1,oldCornerConnections,0,4);
        System.arraycopy(newConnections,5,newMidConnections,0,4);
        System.arraycopy(newConnections,1,oldCornerConnections,0,4);
        boolean cornerConnectionsImproved = false;
        boolean midPointConnectionsImproved = false;
        for (int i : oldCornerConnections) {
            for (int j : newCornerConnections) {
                if (j>=i && j>=2) {
                    cornerConnectionsImproved = true;
                }
                else {
                    cornerConnectionsImproved = false;
                }
            }
        }
        for (int i : oldMidConnections) {
            for (int j : newMidConnections) {
                if (j>=i && j>=3) {
                    midPointConnectionsImproved = true;
                }
                else {
                    midPointConnectionsImproved = false;
                }
            }
        }
        return new boolean[] {cornerConnectionsImproved,midPointConnectionsImproved};
    }

    private int[] checkNumberOfConnections (int[][] designConnectivityArray) {
        int[] numberOfConnections = new int[9];
        for (int i = 0; i < designConnectivityArray.length; i++) {
            for (int j = 0; j < 2; j++) {
                if (designConnectivityArray[i][j] == 1) {
                    numberOfConnections[0] += 1;
                }
                else if (designConnectivityArray[i][j] == 2) {
                    numberOfConnections[1] += 1;
                }
                else if (designConnectivityArray[i][j] == 3) {
                    numberOfConnections[2] += 1;
                }
                else if (designConnectivityArray[i][j] == 4) {
                    numberOfConnections[3] += 1;
                }
                else if (designConnectivityArray[i][j] == 5) {
                    numberOfConnections[4] += 1;
                }
                else if (designConnectivityArray[i][j] == 6) {
                    numberOfConnections[5] += 1;
                }
                else if (designConnectivityArray[i][j] == 7) {
                    numberOfConnections[6] += 1;
                }
                else if (designConnectivityArray[i][j] == 8) {
                    numberOfConnections[7] += 1;
                }
                else if (designConnectivityArray[i][j] == 9) {
                    numberOfConnections[8] += 1;
                }
            }
        }
        int[] cornerNodes = {1,3,7,9};
        int[] midpointNodes = {2,4,6,8};
        int[] lowConnectionNodes = {0,0,0,0,0,0,0,0};
        int numberOfLowConnectionNodes = 0;
        for (int i = 0; i < numberOfConnections.length; i++) {
            int cornerSearchKey = Arrays.binarySearch(cornerNodes, i+1);
            if (cornerSearchKey > 0) {
                if (numberOfConnections[i] < 2) {
                    lowConnectionNodes[numberOfLowConnectionNodes] = i+1;
                    numberOfLowConnectionNodes += 1;
                }
            }
            else {
                int midSearchKey = Arrays.binarySearch(midpointNodes, i+1);
                if (midSearchKey > 0) {
                    if (numberOfConnections[i] < 3) {
                        lowConnectionNodes[numberOfLowConnectionNodes] = i+1;
                        numberOfLowConnectionNodes += 1;
                    }
                }
                else {
                    continue;
                }
            }
        }
        int[] trueLowConnectionNodes = new int[numberOfLowConnectionNodes];
        System.arraycopy(lowConnectionNodes, 0, trueLowConnectionNodes, 0, numberOfLowConnectionNodes);
        return trueLowConnectionNodes;
    }
}
