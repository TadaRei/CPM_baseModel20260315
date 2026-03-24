import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * 複数のパラメータの組み合わせ（直積）を自動生成するクラス
 */
public class ParameterSweep {
    private List<SimulationParams> tasks;

    public ParameterSweep(SimulationParams baseParams) {
        tasks = new ArrayList<>();
        tasks.add(baseParams); // 基準となるパラメータを登録
    }

    // long型（RAND_SEEDなど）を変動させる
    public ParameterSweep varyLong(String paramName, BiConsumer<SimulationParams, Long> setter, long... values) {
        List<SimulationParams> newTasks = new ArrayList<>();
        for (SimulationParams current : tasks) {
            for (long val : values) {
                SimulationParams p = new SimulationParams(current); // コピーを生成
                setter.accept(p, val); // 指定された変数に値をセット
                p.SWEEP_DIR_NAME += (p.SWEEP_DIR_NAME.isEmpty() ? "" : "_") + paramName + "_" + val;
                newTasks.add(p);
            }
        }
        tasks = newTasks;
        return this;
    }

    // int型（TARGET_AREAなど）を変動させる
    public ParameterSweep varyInt(String paramName, BiConsumer<SimulationParams, Integer> setter, int... values) {
        List<SimulationParams> newTasks = new ArrayList<>();
        for (SimulationParams current : tasks) {
            for (int val : values) {
                SimulationParams p = new SimulationParams(current);
                setter.accept(p, val);
                p.SWEEP_DIR_NAME += (p.SWEEP_DIR_NAME.isEmpty() ? "" : "_") + paramName + "_" + val;
                newTasks.add(p);
            }
        }
        tasks = newTasks;
        return this;
    }

    // double型（TEMPERATUREなど）を変動させる
    public ParameterSweep varyDouble(String paramName, BiConsumer<SimulationParams, Double> setter, double... values) {
        List<SimulationParams> newTasks = new ArrayList<>();
        for (SimulationParams current : tasks) {
            for (double val : values) {
                SimulationParams p = new SimulationParams(current);
                setter.accept(p, val);
                p.SWEEP_DIR_NAME += (p.SWEEP_DIR_NAME.isEmpty() ? "" : "_") + paramName + "_" + val;
                newTasks.add(p);
            }
        }
        tasks = newTasks;
        return this;
    }

    // 最終的に生成された全パターンのリストを取得
    public List<SimulationParams> build() {
        return tasks;
    }
}