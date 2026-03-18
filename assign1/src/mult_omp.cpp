#include <omp.h>

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

static void printResult(const RunResult& result, const string& variant, int size, int threads, bool csv)
{
    if (csv) {
        cout << variant << "," << size << "," << threads << "," << fixed << setprecision(6) << result.seconds << "," << fixed << setprecision(6) << result.gflops << endl;
        return;
    }

    cout << fixed << setprecision(3);
    cout << "Variant: " << variant << endl;
    cout << "Threads: " << threads << endl;
    cout << "Time: " << result.seconds << " seconds" << endl;
    cout << "GFLOPS: " << setprecision(6) << result.gflops << endl;
}

static void printMatrix(const double* phc, int size)
{
    cout << "Result matrix: " << endl;
    for (int j = 0; j < min(10, size); j++) {
        cout << phc[j] << " ";
    }
    cout << endl;
}

static void initializeMatrices(double* pha, double* phb, int size)
{
    for (int i = 0; i < size; i++) {
        for (int j = 0; j < size; j++) {
            pha[i * size + j] = 1.0;
            phb[i * size + j] = static_cast<double>(i + 1);
        }
    }
}

static void zeroMatrix(double* phc, int size)
{
    for (int i = 0; i < size * size; i++) {
        phc[i] = 0.0;
    }
}

// Version 1, first parallel approach, parallelize the outer loop over i.
static RunResult onMultParallel1(int size, int threads, bool csv)
{
    double *pha, *phb, *phc;

    pha = (double *)malloc((size * size) * sizeof(double));
    phb = (double *)malloc((size * size) * sizeof(double));
    phc = (double *)malloc((size * size) * sizeof(double));

    initializeMatrices(pha, phb, size);
    omp_set_num_threads(threads);

    const auto time1 = chrono::steady_clock::now();

    #pragma omp parallel for
    for (int i = 0; i < size; i++) {
        for (int j = 0; j < size; j++) {
            double temp = 0.0;
            for (int k = 0; k < size; k++) {
                temp += pha[i * size + k] * phb[k * size + j];
            }
            phc[i * size + j] = temp;
        }
    }

    const auto time2 = chrono::steady_clock::now();
    const RunResult result = buildResult(size, chrono::duration<double>(time2 - time1).count());
    printResult(result, "parallel1", size, threads, csv);

    if (!csv) {
        printMatrix(phc, size);
    }

    free(pha);
    free(phb);
    free(phc);
    return result;
}

// Version 1, second parallel approach, use omp for only in the inner loop over k.
static RunResult onMultParallel2(int size, int threads, bool csv)
{
    double *pha, *phb, *phc;

    pha = (double *)malloc((size * size) * sizeof(double));
    phb = (double *)malloc((size * size) * sizeof(double));
    phc = (double *)malloc((size * size) * sizeof(double));

    initializeMatrices(pha, phb, size);
    omp_set_num_threads(threads);

    const auto time1 = chrono::steady_clock::now();

    #pragma omp parallel
    {
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                double temp = 0.0;
                #pragma omp for
                for (int k = 0; k < size; k++) {
                    temp += pha[i * size + k] * phb[k * size + j];
                }
                phc[i * size + j] = temp;
            }
        }
    }

    const auto time2 = chrono::steady_clock::now();
    const RunResult result = buildResult(size, chrono::duration<double>(time2 - time1).count());
    printResult(result, "parallel2", size, threads, csv);

    if (!csv) {
        printMatrix(phc, size);
    }

    free(pha);
    free(phb);
    free(phc);
    return result;
}

// Version 2, first parallel approach, parallelize the outer loop over i.
static RunResult onMultLineParallel1(int size, int threads, bool csv)
{
    double *pha, *phb, *phc;

    pha = (double *)malloc((size * size) * sizeof(double));
    phb = (double *)malloc((size * size) * sizeof(double));
    phc = (double *)malloc((size * size) * sizeof(double));

    initializeMatrices(pha, phb, size);
    zeroMatrix(phc, size);
    omp_set_num_threads(threads);

    const auto time1 = chrono::steady_clock::now();

    #pragma omp parallel for
    for (int i = 0; i < size; i++) {
        for (int k = 0; k < size; k++) {
            for (int j = 0; j < size; j++) {
                phc[i * size + j] += pha[i * size + k] * phb[k * size + j];
            }
        }
    }

    const auto time2 = chrono::steady_clock::now();
    const RunResult result = buildResult(size, chrono::duration<double>(time2 - time1).count());
    printResult(result, "lineParallel1", size, threads, csv);

    if (!csv) {
        printMatrix(phc, size);
    }

    free(pha);
    free(phb);
    free(phc);
    return result;
}

// Version 2, use omp for simd on the inner loop over j.
static RunResult onMultLineSimd(int size, int threads, bool csv)
{
    double *pha, *phb, *phc;

    pha = (double *)malloc((size * size) * sizeof(double));
    phb = (double *)malloc((size * size) * sizeof(double));
    phc = (double *)malloc((size * size) * sizeof(double));

    initializeMatrices(pha, phb, size);
    zeroMatrix(phc, size);
    omp_set_num_threads(threads);

    const auto time1 = chrono::steady_clock::now();

    #pragma omp parallel
    {
        for (int i = 0; i < size; i++) {
            for (int k = 0; k < size; k++) {
                #pragma omp for simd
                for (int j = 0; j < size; j++) {
                    phc[i * size + j] += pha[i * size + k] * phb[k * size + j];
                }
            }
        }
    }

    const auto time2 = chrono::steady_clock::now();
    const RunResult result = buildResult(size, chrono::duration<double>(time2 - time1).count());
    printResult(result, "lineSimd", size, threads, csv);

    if (!csv) {
        printMatrix(phc, size);
    }

    free(pha);
    free(phb);
    free(phc);
    return result;
}

// Version 2, use collapse(2) on loops i and k.
static RunResult onMultLineCollapse(int size, int threads, bool csv)
{
    double *pha, *phb, *phc;

    pha = (double *)malloc((size * size) * sizeof(double));
    phb = (double *)malloc((size * size) * sizeof(double));
    phc = (double *)malloc((size * size) * sizeof(double));

    initializeMatrices(pha, phb, size);
    zeroMatrix(phc, size);
    omp_set_num_threads(threads);

    const auto time1 = chrono::steady_clock::now();

    #pragma omp parallel for collapse(2)
    for (int i = 0; i < size; i++) {
        for (int k = 0; k < size; k++) {
            for (int j = 0; j < size; j++) {
                #pragma omp atomic
                phc[i * size + j] += pha[i * size + k] * phb[k * size + j];
            }
        }
    }

    const auto time2 = chrono::steady_clock::now();
    const RunResult result = buildResult(size, chrono::duration<double>(time2 - time1).count());
    printResult(result, "lineCollapse", size, threads, csv);

    if (!csv) {
        printMatrix(phc, size);
    }

    free(pha);
    free(phb);
    free(phc);
    return result;
}

int main(int argc, char *argv[])
{
    int size, threads;
    int op;

    if (argc >= 4) {
        op = atoi(argv[1]);
        size = atoi(argv[2]);
        threads = atoi(argv[3]);
        const bool csv = (argc >= 5) && (string(argv[4]) == "--csv");

        switch (op) {
            case 1:
                onMultParallel1(size, threads, csv);
                return 0;
            case 2:
                onMultParallel2(size, threads, csv);
                return 0;
            case 3:
                onMultLineParallel1(size, threads, csv);
                return 0;
            case 4:
                onMultLineSimd(size, threads, csv);
                return 0;
            case 5:
                onMultLineCollapse(size, threads, csv);
                return 0;
            default:
                cerr << "Usage: ./mult_omp_cpp <1|2|3|4|5> <size> <threads> [--csv]" << endl;
                cerr << "1: parallel1" << endl;
                cerr << "2: parallel2" << endl;
                cerr << "3: lineParallel1" << endl;
                cerr << "4: lineSimd" << endl;
                cerr << "5: lineCollapse" << endl;
                return 1;
        }
    }

    cerr << "Usage: ./mult_omp_cpp <1|2|3|4|5> <size> <threads> [--csv]" << endl;
    cerr << "1: parallel1" << endl;
    cerr << "2: parallel2" << endl;
    cerr << "3: lineParallel1" << endl;
    cerr << "4: lineSimd" << endl;
    cerr << "5: lineCollapse" << endl;

    return 1;
}
