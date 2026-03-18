#include <stdio.h>
#include <algorithm>
#include <chrono>
#include <cstdlib>
#include <iomanip>
#include <iostream>
#include <string>

using namespace std;

struct RunResult {
    double seconds;
    double gflops;
};

static RunResult buildResult(int size, double seconds)
{
    const double n = static_cast<double>(size);
    const double flops = 2.0 * n * n * n;
    return {seconds, flops / (seconds * 1e9)};
}

static void printResult(const RunResult& result, const string& language, const string& variant, int size, int blockSize, bool csv)
{
    if (csv) {
        cout << language << "," << variant << "," << size << "," << blockSize << "," << fixed << setprecision(6) << result.seconds << ","<< fixed << setprecision(6) << result.gflops << endl;
        return;
    }

    cout << fixed << setprecision(3);
    cout << "Time: " << result.seconds << " seconds" << endl;
    cout << "GFLOPS: " << setprecision(6) << result.gflops << endl;
}

static void printMatrix(const double* phc, int m_br)
{
    cout << "Result matrix: " << endl;
    for (int j = 0; j < min(10, m_br); j++) {
        cout << phc[j] << " ";
    }
    cout << endl;
}

static void initializeMatrices(double* pha, double* phb, int m_ar, int m_br)
{
    for (int i = 0; i < m_ar; i++) {
        for (int j = 0; j < m_ar; j++) {
            pha[i * m_ar + j] = 1.0;
        }
    }

    for (int i = 0; i < m_ar; i++) {
        for (int j = 0; j < m_br; j++) {
            phb[i * m_br + j] = (double)(i + 1);
        }
    }
}

RunResult OnMult(int m_ar, int m_br, bool csv = false)
{
    double temp;
    int i, j, k;

    double *pha, *phb, *phc;

    pha = (double *)malloc((m_ar * m_ar) * sizeof(double));
    phb = (double *)malloc((m_ar * m_ar) * sizeof(double));
    phc = (double *)malloc((m_ar * m_ar) * sizeof(double));

    initializeMatrices(pha, phb, m_ar, m_br);

    const auto time1 = chrono::steady_clock::now();

    for(i = 0; i < m_ar; i++)
    {
        for(j = 0; j < m_br; j++)
        {
            temp = 0;
            for(k = 0; k < m_ar; k++)
            {    
                temp += pha[i*m_ar + k] * phb[k*m_br + j];
            }
            phc[i*m_ar + j] = temp;
        }
    }

    const auto time2 = chrono::steady_clock::now();
    const double seconds = chrono::duration<double>(time2 - time1).count();
    const RunResult result = buildResult(m_ar, seconds);
    printResult(result, "cpp", "standard", m_ar, 0, csv);

    if (!csv) {
        printMatrix(phc, m_br);
    }

    free(pha);
    free(phb);
    free(phc);
    return result;
}


RunResult OnMultLine(int m_ar, int m_br, bool csv = false)
{
    int i, k, j;

    double *pha, *phb, *phc;

    pha = (double *)malloc((m_ar * m_ar) * sizeof(double));
    phb = (double *)malloc((m_ar * m_ar) * sizeof(double));
    phc = (double *)malloc((m_ar * m_ar) * sizeof(double));

    initializeMatrices(pha, phb, m_ar, m_br);

    for(i = 0; i < m_ar * m_br; i++)
        phc[i] = 0.0;

    const auto time1 = chrono::steady_clock::now();

    for(i = 0; i < m_ar; i++)
    {
        for(k = 0; k < m_ar; k++)
        {
            for(j = 0; j < m_br; j++)
            {
                phc[i*m_ar + j] += pha[i*m_ar + k] * phb[k*m_br + j];
            }
        }
    }

    const auto time2 = chrono::steady_clock::now();
    const double seconds = chrono::duration<double>(time2 - time1).count();
    const RunResult result = buildResult(m_ar, seconds);
    printResult(result, "cpp", "line", m_ar, 0, csv);

    if (!csv) {
        printMatrix(phc, m_br);
    }

    free(pha);
    free(phb);
    free(phc);
    return result;
}


RunResult OnMultBlock(int m_ar, int m_br, int bkSize, bool csv = false)
{
    int i, j, k;
    int ii, jj, kk;

    double *pha, *phb, *phc;

    pha = (double *)malloc((m_ar * m_ar) * sizeof(double));
    phb = (double *)malloc((m_ar * m_ar) * sizeof(double));
    phc = (double *)malloc((m_ar * m_ar) * sizeof(double));

    initializeMatrices(pha, phb, m_ar, m_br);

    for(i = 0; i < m_ar * m_br; i++)
        phc[i] = 0.0;

    const auto time1 = chrono::steady_clock::now();

    for(ii = 0; ii < m_ar; ii += bkSize)
    {
        for(kk = 0; kk < m_ar; kk += bkSize)
        {
            for(jj = 0; jj < m_br; jj += bkSize)
            {
                for(i = ii; i < min(ii + bkSize, m_ar); i++)
                {
                    for(k = kk; k < min(kk + bkSize, m_ar); k++)
                    {
                        for(j = jj; j < min(jj + bkSize, m_br); j++)
                        {
                            phc[i*m_ar + j] += pha[i*m_ar + k] * phb[k*m_br + j];
                        }
                    }
                }
            }
        }
    }

    const auto time2 = chrono::steady_clock::now();
    const double seconds = chrono::duration<double>(time2 - time1).count();
    const RunResult result = buildResult(m_ar, seconds);
    printResult(result, "cpp", "block", m_ar, bkSize, csv);

    if (!csv) {
        printMatrix(phc, m_br);
    }

    free(pha);
    free(phb);
    free(phc);
    return result;
}


int main(int argc, char *argv[])
{
    int lin, col, blockSize;
    int op;

    if (argc >= 3) {
        op = atoi(argv[1]);
        lin = atoi(argv[2]);
        col = lin;

        if (op == 1) {
            const bool csv = (argc >= 4) && (string(argv[3]) == "--csv");
            OnMult(lin, col, csv);
            return 0;
        }

        if (op == 2) {
            const bool csv = (argc >= 4) && (string(argv[3]) == "--csv");
            OnMultLine(lin, col, csv);
            return 0;
        }

        if (op == 3) {
            if (argc < 4) {
                cerr << "Usage: ./mult_cpp 3 <size> <block_size> [--csv]" << endl;
                return 1;
            }

            blockSize = atoi(argv[3]);
            const bool csv = (argc >= 5) && (string(argv[4]) == "--csv");
            OnMultBlock(lin, col, blockSize, csv);
            return 0;
        }

        cerr << "Usage: ./mult_cpp <1|2|3> <size> [block_size] [--csv]" << endl;
        return 1;
    }

    do {
        cout << endl << "1. Multiplication" << endl;
        cout << "2. Line Multiplication" << endl;
        cout << "3. Block Multiplication" << endl;
        cout << "0. Exit" << endl;
        cout << "Selection?: ";
        cin >> op;

        if (op == 0)
            break;

        cout << "Dimensions: lins=cols ? ";
        cin >> lin;
        col = lin;

        switch (op) {
            case 1:
                OnMult(lin, col);
                break;
            case 2:
                OnMultLine(lin, col);
                break;
            case 3:
                cout << "Block Size? ";
                cin >> blockSize;
                OnMultBlock(lin, col, blockSize);
                break;
            default:
                break;
        }

    } while (op != 0);

    return 0;
}