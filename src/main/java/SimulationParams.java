import java.util.Arrays;

/**
 * シミュレーション設定パラメータを保持するクラス。
 * staticを廃止し、スレッドごとにインスタンス化して値を変更できるようにしています。
 */
public class SimulationParams {

    // --- グリッド・基本設定 ---
    public int GRID_WIDTH;
    public int GRID_HEIGHT;
    public int NUM_CELLS;
    public int SIMULATION_MCS;
    public String OUTPUT_FOLDER;
    public long RAND_SEED; // intからlongに変更（より一般的なシード型に対応）

    // --- 出力設定 ---
    public String IMAGE_FILE_PREFIX;
    public String IMAGE_FILE_EXTENSION;
    public int CELL_SIZE;
    public String SWEEP_DIR_NAME = "";

    // --- 細胞特性（CPMパラメータ） ---
    public int TARGET_AREA;
    public int TARGET_PERIMETER;
    public double K_AREA; // 計算精度のためdouble推奨ですが、元のintに合わせるならintでも可
    public double K_PERI; 
    public double TEMPERATURE;

    // --- 接着エネルギー行列 ---
    public int[][] J;

    /**
     * デフォルトコンストラクタ
     * 元のParametersクラスで定義されていた初期値を設定します。
     */
    public SimulationParams() {
        this.GRID_WIDTH  = 1000;//700
        this.GRID_HEIGHT = 1000;//700
        this.NUM_CELLS = 101;//601
        this.SIMULATION_MCS = 100;
        this.OUTPUT_FOLDER = "./output";
        this.RAND_SEED = 0;

        this.IMAGE_FILE_PREFIX = "frame_";
        this.IMAGE_FILE_EXTENSION = "png";
        this.CELL_SIZE = 5;

        this.TARGET_AREA = 120;
        this.TARGET_PERIMETER = 70;
        this.K_AREA = 5.0; // 柔軟性のためdoubleにしていますがintが必要ならキャストしてください
        this.K_PERI = 5.0;
        this.TEMPERATURE = 10.0;

        // 行列の初期化（ディープコピーを行う）
        this.J = new int[][]{
            {0, 20},
            {20, 80}
        };
    }

    /**
     * コピーコンストラクタ
     * 既存のparamsを複製します。一部の値だけ変えたい時に便利です。
     */
    public SimulationParams(SimulationParams source) {
        this.GRID_WIDTH = source.GRID_WIDTH;
        this.GRID_HEIGHT = source.GRID_HEIGHT;
        this.NUM_CELLS = source.NUM_CELLS;
        this.SIMULATION_MCS = source.SIMULATION_MCS;
        this.CELL_SIZE = source.CELL_SIZE;
        this.OUTPUT_FOLDER = source.OUTPUT_FOLDER;
        this.RAND_SEED = source.RAND_SEED;

        this.IMAGE_FILE_PREFIX = source.IMAGE_FILE_PREFIX;
        this.IMAGE_FILE_EXTENSION = source.IMAGE_FILE_EXTENSION;
        this.SWEEP_DIR_NAME = source.SWEEP_DIR_NAME;

        this.TARGET_AREA = source.TARGET_AREA;
        this.TARGET_PERIMETER = source.TARGET_PERIMETER;
        this.K_AREA = source.K_AREA;
        this.K_PERI = source.K_PERI;
        this.TEMPERATURE = source.TEMPERATURE;

        // 配列のディープコピー（参照渡しを防ぐため）
        this.J = new int[source.J.length][];
        for (int i = 0; i < source.J.length; i++) {
            this.J[i] = source.J[i].clone();
        }
    }

    /**
     * パラメータ一覧を文字列で返すメソッド
     */
    public String getParametersString() {
        StringBuilder sb = new StringBuilder();
        sb.append("GRID_WIDTH=").append(GRID_WIDTH).append("\n");
        sb.append("GRID_HEIGHT=").append(GRID_HEIGHT).append("\n");
        sb.append("NUM_CELLS=").append(NUM_CELLS).append("\n");
        sb.append("SIMULATION_MCS=").append(SIMULATION_MCS).append("\n");
        sb.append("RAND_SEED=").append(RAND_SEED).append("\n");
        sb.append("TARGET_AREA=").append(TARGET_AREA).append("\n");
        sb.append("TARGET_PERIMETER=").append(TARGET_PERIMETER).append("\n");
        sb.append("K_AREA=").append(K_AREA).append("\n");
        sb.append("K_PERI=").append(K_PERI).append("\n");
        sb.append("TEMPERATURE=").append(TEMPERATURE).append("\n");
        sb.append("J=").append(Arrays.deepToString(J)).append("\n");
        return sb.toString();
    }
}