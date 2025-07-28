import org.json.JSONObject;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Main class to run the Catalog Placements Assignment.
 * It reads test cases from JSON files, uses the ShamirSolver to compute the secrets,
 * and prints the results for both cases simultaneously.
 */
public class AssignmentRunner {

    public static void main(String[] args) {
        // Define the file paths for the test cases.
        String[] testCaseFiles = {"testcase1.json", "testcase2.json"};
        
        System.out.println("Starting secret computation for assignment test cases...");
        System.out.println("-----------------------------------------------------");

        for (int i = 0; i < testCaseFiles.length; i++) {
            String fileName = testCaseFiles[i];
            try {
                // Read the entire file content into a string.
                String jsonString = Files.readString(Paths.get(fileName));
                
                // Create a solver instance for the specific test case data.
                ShamirSolver solver = new ShamirSolver(jsonString);
                
                // Find the secret.
                BigInteger secret = solver.findSecret();
                
                // Print the result in a clean format.
                System.out.printf("Secret for Test Case %d (%s): %s\n", (i + 1), fileName, secret);

            } catch (IOException e) {
                System.err.printf("Error reading file '%s'. Please ensure it's in the project root directory.\n", fileName);
            } catch (Exception e) {
                // Catch any other errors during computation.
                System.err.printf("An error occurred while processing '%s': %s\n", fileName, e.getMessage());
                e.printStackTrace();
            }
        }
        System.out.println("-----------------------------------------------------");
        System.out.println("Computation finished.");
    }
}

/**
 * Implements the core logic for solving Shamir's Secret Sharing.
 * It is instantiated with the problem's data and can compute the secret.
 */
class ShamirSolver {

    private final int k;
    private final List<Point> allPoints;

    /**
     * Inner class representing a point (x, y) on the polynomial.
     * Uses BigInteger to support numbers up to 256-bit as required.
     */
    private static class Point {
        final BigInteger x;
        final BigInteger y;

        Point(BigInteger x, BigInteger y) { this.x = x; this.y = y; }
    }

    /**
     * Inner class for precise fractional arithmetic using BigIntegers,
     * which is essential for the division in Lagrange interpolation.
     */
    private static class Fraction {
        final BigInteger num;
        final BigInteger den;

        Fraction(BigInteger num) { this.num = num; this.den = BigInteger.ONE; }
        Fraction(BigInteger num, BigInteger den) {
            if (den.equals(BigInteger.ZERO)) throw new IllegalArgumentException("Denominator cannot be zero.");
            BigInteger common = num.gcd(den);
            this.num = num.divide(common);
            this.den = den.divide(common);
        }

        Fraction add(Fraction other) {
            BigInteger newNum = this.num.multiply(other.den).add(other.num.multiply(this.den));
            BigInteger newDen = this.den.multiply(other.den);
            return new Fraction(newNum, newDen);
        }
        Fraction multiply(Fraction other) { return new Fraction(this.num.multiply(other.num), this.den.multiply(other.den)); }
        boolean isInteger() { return this.den.equals(BigInteger.ONE); }
        BigInteger toBigInteger() {
            if (!isInteger()) throw new IllegalStateException("Fraction is not a whole number.");
            return this.num;
        }
    }

    /**
     * Constructs the solver by parsing the input JSON and decoding the shares.
     * CHECKPOINT 1: Read Test Case & CHECKPOINT 2: Decode Y Values.
     *
     * @param jsonString The raw JSON data for the problem.
     */
    public ShamirSolver(String jsonString) {
        JSONObject data = new JSONObject(jsonString);
        this.k = data.getJSONObject("keys").getInt("k");
        this.allPoints = new ArrayList<>();
        
        for (String key : data.keySet()) {
            if (!key.equals("keys")) {
                JSONObject shareObject = data.getJSONObject(key);
                BigInteger x = new BigInteger(key);
                int base = Integer.parseInt(shareObject.getString("base"));
                BigInteger y = new BigInteger(shareObject.getString("value"), base);
                this.allPoints.add(new Point(x, y));
            }
        }
    }

    /**
     * Finds the secret by testing all combinations of k shares.
     * CHECKPOINT 3: Find the Secret (C).
     *
     * @return The secret 'c', which is the constant term of the polynomial.
     */
    public BigInteger findSecret() {
        List<List<Point>> combinations = new ArrayList<>();
        generateCombinations(0, new ArrayList<>(), combinations);

        Map<BigInteger, Integer> secretFrequencies = new HashMap<>();
        for (List<Point> combo : combinations) {
            // Use Lagrange Interpolation to find f(0), which is the secret 'c'.
            Fraction secret = calculateSecretForCombination(combo);
            
            if (secret.isInteger()) {
                BigInteger intSecret = secret.toBigInteger();
                secretFrequencies.put(intSecret, secretFrequencies.getOrDefault(intSecret, 0) + 1);
            }
        }
        
        if (secretFrequencies.isEmpty()) {
            throw new RuntimeException("No valid integer secret could be found for any combination.");
        }
        
        // The correct secret is the one that appears most frequently.
        return Collections.max(secretFrequencies.entrySet(), Map.Entry.comparingByValue()).getKey();
    }
    
    /**
     * Calculates the secret for a specific combination of k points using Lagrange Interpolation.
     *
     * @param combo A list of exactly k points.
     * @return The calculated secret as a Fraction.
     */
    private Fraction calculateSecretForCombination(List<Point> combo) {
        Fraction secretSum = new Fraction(BigInteger.ZERO);
        for (int j = 0; j < this.k; j++) {
            Point currentPoint = combo.get(j);
            Fraction lagrangeBasisAtZero = new Fraction(BigInteger.ONE);
            for (int m = 0; m < this.k; m++) {
                if (m != j) {
                    Point otherPoint = combo.get(m);
                    BigInteger numerator = otherPoint.x.negate();
                    BigInteger denominator = currentPoint.x.subtract(otherPoint.x);
                    lagrangeBasisAtZero = lagrangeBasisAtZero.multiply(new Fraction(numerator, denominator));
                }
            }
            secretSum = secretSum.add(new Fraction(currentPoint.y).multiply(lagrangeBasisAtZero));
        }
        return secretSum;
    }

    /**
     * Helper method to recursively generate all combinations of k points.
     */
    private void generateCombinations(int start, List<Point> current, List<List<Point>> combos) {
        if (current.size() == this.k) {
            combos.add(new ArrayList<>(current));
            return;
        }
        for (int i = start; i < this.allPoints.size(); i++) {
            current.add(this.allPoints.get(i));
            generateCombinations(i + 1, current, combos);
            current.remove(current.size() - 1); // Backtrack
        }
    }
}