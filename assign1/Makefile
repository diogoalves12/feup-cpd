CXX := g++
CXXFLAGS := -O2 -std=c++17 -Wall -Wextra
OMP_CXXFLAGS := -O2 -std=c++17 -Wall -Wextra -fopenmp
JAVA := javac

SRC_DIR := src
BUILD_DIR := build
CPP_TARGET := $(BUILD_DIR)/mult_cpp
OMP_TARGET := $(BUILD_DIR)/mult_omp_cpp
JAVA_TARGET := $(BUILD_DIR)/mult.class

.PHONY: all cpp omp java clean

all: cpp java

cpp: $(CPP_TARGET)

omp: $(OMP_TARGET)

java: $(JAVA_TARGET)

$(BUILD_DIR):
	mkdir -p $(BUILD_DIR)

$(CPP_TARGET): $(SRC_DIR)/mult.cpp | $(BUILD_DIR)
	$(CXX) $(CXXFLAGS) $< -o $@

$(OMP_TARGET): $(SRC_DIR)/mult_omp.cpp | $(BUILD_DIR)
	$(CXX) $(OMP_CXXFLAGS) $< -o $@

$(JAVA_TARGET): $(SRC_DIR)/mult.java | $(BUILD_DIR)
	$(JAVA) -d $(BUILD_DIR) $<

clean:
	rm -rf $(BUILD_DIR)
