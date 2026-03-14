import java.util.Scanner;

public class mult {

    public static void onMult(int m_ar, int m_br) {
        long time1, time2;

        double temp;
        int i, j, k;

        double[] pha = new double[m_ar * m_ar];
        double[] phb = new double[m_ar * m_ar];
        double[] phc = new double[m_ar * m_ar];

        for (i = 0; i < m_ar; i++) {
            for (j = 0; j < m_ar; j++) {
                pha[i * m_ar + j] = 1.0;
            }
        }

        for (i = 0; i < m_br; i++) {
            for (j = 0; j < m_br; j++) {
                phb[i * m_br + j] = (double) (i + 1);
            }
        }

        time1 = System.nanoTime();

        for (i = 0; i < m_ar; i++) {
            for (j = 0; j < m_br; j++) {
                temp = 0;
                for (k = 0; k < m_ar; k++) {
                    temp += pha[i * m_ar + k] * phb[k * m_br + j];
                }
                phc[i * m_ar + j] = temp;
            }
        }

        time2 = System.nanoTime();
        double seconds = (time2 - time1) / 1_000_000_000.0;
        System.out.printf("Time: %.3f seconds%n", seconds);

        System.out.println("Result matrix: ");
        for (j = 0; j < Math.min(10, m_br); j++) {
            System.out.print(phc[j] + " ");
        }
        System.out.println();
    }

    public static void onMultLine(int m_ar, int m_br) {
        long time1, time2;

        int i, j, k;

        double[] pha = new double[m_ar * m_ar];
        double[] phb = new double[m_ar * m_ar];
        double[] phc = new double[m_ar * m_ar];

        for (i = 0; i < m_ar; i++) {
            for (j = 0; j < m_ar; j++) {
                pha[i * m_ar + j] = 1.0;
            }
        }

        for (i = 0; i < m_br; i++) {
            for (j = 0; j < m_br; j++) {
                phb[i * m_br + j] = (double) (i + 1);
            }
        }

        for (i = 0; i < m_ar * m_ar; i++) {
            phc[i] = 0.0;
        }

        time1 = System.nanoTime();

        for (i = 0; i < m_ar; i++) {
            for (k = 0; k < m_ar; k++) {
                for (j = 0; j < m_br; j++) {
                    phc[i * m_ar + j] += pha[i * m_ar + k] * phb[k * m_br + j];
                }
            }
        }

        time2 = System.nanoTime();
        double seconds = (time2 - time1) / 1_000_000_000.0;
        System.out.printf("Time: %.3f seconds%n", seconds);

        System.out.println("Result matrix: ");
        for (j = 0; j < Math.min(10, m_br); j++) {
            System.out.print(phc[j] + " ");
        }
        System.out.println();
    }

    public static void main(String[] args) {
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
                    onMult(lin, col);
                    break;
                case 2:
                    onMultLine(lin, col);
                    break;
                default:
                    break;
            }
        } while (op != 0);

        scanner.close();
    }
}
