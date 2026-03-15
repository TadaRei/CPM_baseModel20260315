public interface ISimulator {
    void runMonteCarloStep();
    Grid getGrid();
    double getNumSteps();
    void ticks(); // GPU版になかった場合は空の実装をします
}