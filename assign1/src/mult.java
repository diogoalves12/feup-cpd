import java.util.Locale;
import java.util.Scanner;

public class mult {
    private static class RunResult {
        final double seconds;
        final double gflops;

        RunResult(double seconds, double gflops) {
            this.seconds = seconds;
            this.gflops = gflops;
        }
    }

    private static RunResult buildResult(int size, double seconds) {
        double n = (double) size;
        double flops = 2.0 * n * n * n;
        return new RunResult(seconds, flops / (seconds * 1e9));
    }

    private static void printResult(RunResult result, String language, String variant, int size, int blockSize, boolean csv) {
        if (csv) {
            System.out.printf(Locale.US, "%s,%s,%d,%d,%.6f,%.6f%n",language, variant, size, blockSize, result.seconds, result.gflops);
            return;
        }
        System.out.printf(Locale.US, "Time: %.3f seconds%n", result.seconds);
        System.out.printf(Locale.US, "GFLOPS: %.6f%n", result.gflops);
    }

    private static void printMatrix(double[] phc, int m_br) {
        System.out.println("Result matrix: ");
        for (int j = 0; j < Math.min(10, m_br); j++) {
            System.out.print(phc[j] + " ");
        }
        System.out.println();
    }

    private static void initializeMatrices(double[] pha, double[] phb, int m_ar, int m_br) {
        for (int i = 0; i < m_ar; i++) {
            for (int j = 0; j < m_ar; j++) {
                pha[i * m_ar + j] = 1.0;
            }
        }

        for (int i = 0; i < m_ar; i++) {
            for (int j = 0; j < m_br; j++) {
                phb[i * m_br + j] = (double) (i + 1);
            }
        }
    }

    private static RunResult OnMult(int m_ar, int m_br, boolean csv) {
        double temp;
        int i, j, k;

        double[] pha = new double[m_ar * m_ar];
        double[] phb = new double[m_ar * m_br];
        double[] phc = new double[m_ar * m_br];

        initializeMatrices(pha, phb, m_ar, m_br);

        long time1 = System.nanoTime();

        for (i = 0; i < m_ar; i++) {
            for (j = 0; j < m_br; j++) {
                temp = 0;
                for (k = 0; k < m_ar; k++) {
                    temp += pha[i * m_ar + k] * phb[k * m_br + j];
                }
                phc[i * m_ar + j] = temp;
            }
        }

        long time2 = System.nanoTime();
        RunResult result = buildResult(m_ar, (time2 - time1) / 1_000_000_000.0);
        printResult(result, "java", "standard", m_ar, 0, csv);

        if (!csv) {
            printMatrix(phc, m_br);
        }

        return result;
    }

    private static RunResult OnMultLine(int m_ar, int m_br, boolean csv) {
        int i, j, k;

        double[] pha = new double[m_ar * m_ar];
        double[] phb = new double[m_ar * m_br];
        double[] phc = new double[m_ar * m_br];

        initializeMatrices(pha, phb, m_ar, m_br);

        for (i = 0; i < m_ar * m_br; i++) {
            phc[i] = 0.0;
        }

        long time1 = System.nanoTime();

        for (i = 0; i < m_ar; i++) {
            for (k = 0; k < m_ar; k++) {
                for (j = 0; j < m_br; j++) {
                    phc[i * m_ar + j] += pha[i * m_ar + k] * phb[k * m_br + j];
                }
            }
        }

        long time2 = System.nanoTime();
        RunResult result = buildResult(m_ar, (time2 - time1) / 1_000_000_000.0);
        printResult(result, "java", "line", m_ar, 0, csv);

        if (!csv) {
            printMatrix(phc, m_br);
        }

        return result;
    }

    public static void main(String[] args) {
        if (args.length >= 2) {
            int op = Integer.parseInt(args[0]);
            int lin = Integer.parseInt(args[1]);
            boolean csv = (args.length >= 3) && ("--csv".equals(args[2]));

            switch (op) {
                case 1:
                    OnMult(lin, lin, csv);
                    return;
                case 2:
                    OnMultLine(lin, lin, csv);
                    return;
                default:
                    System.err.println("Usage: java mult <1|2> <size> [--csv]");
                    System.exit(1);
            }
        }

        int lin, col;
        int op;
        Scanner scanner = new Scanner(System.in);

        do {
            System.out.println();
            System.out.println("1. Multiplication");
            System.out.println("2. Line Multiplication");
            System.out.println("0. Exit");
            System.out.print("Selection?: ");
            op = scanner.nextInt();

            if (op == 0) {
                break;
            }

            System.out.print("Dimensions: lins=cols ? ");
            lin = scanner.nextInt();
            col = lin;

            switch (op) {
                case 1:
                    OnMult(lin, col, false);
                    break;
                case 2:
                    OnMultLine(lin, col, false);
                    break;
                default:
                    break;
            }
        } while (op != 0);

        scanner.close();
    }
}
