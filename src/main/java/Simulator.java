import java.awt.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Simulatorクラスは、シミュレーションの更新（セルの動きなど）を管理します。
 * Gridクラスの状態を元に、ランダムなセル更新処理を行い、エネルギー変化に基づいてその変更を確率的に受け入れます。
 */
class Simulator implements ISimulator{
	private SimulationParams params;
	
	private Grid grid;      // シミュレーション対象のグリッド（場）のオブジェクト
	private Random rand;    // ランダムな数値生成用のオブジェクト。更新時のランダム選択に利用します。
	private double num_steps = 0.0;
	private int num_trans = 0;

	/**
	 * コンストラクタ
	 * 指定された幅と高さのグリッドを初期化し、ランダムオブジェクトも生成します。
	 * @param width グリッドの横幅（セル数）
	 * @param height グリッドの縦幅（セル数）
	 */
	public Simulator(SimulationParams p, int width, int height) {
		params = p;
		
		grid = new Grid(p, width, height); // 指定サイズのグリッドを生成
		rand = new Random(params.RAND_SEED);        // ランダムな更新処理のためのオブジェクトを生成
	}

	/**
     * 境界リストを用いた最適化を適用して、1モンテカルロステップ（MCS）分シミュレーションを進めます。
     */
    public void runMonteCarloStep() {
    	
        // 2. 更新候補となる境界リストを準備する
        List<Point> allPerimeterPoints = new ArrayList<>();
        Set<java.awt.Point>[] perimeterMap = grid.getCellPerimeterCoords();
        
        for (Set<Point> points : perimeterMap) {
            // points が null じゃなく、かつ中身が入っている時だけ追加する
            if (points != null && !points.isEmpty()) {
                allPerimeterPoints.addAll(points);
            }
        }
        
        if (allPerimeterPoints.isEmpty()) {
            return; // 何もせず終了
        }
        
        num_steps += 1.0/allPerimeterPoints.size();
        
        // 境界ピクセルが一つもなければ、何もせず終了
        if (allPerimeterPoints.isEmpty()) return;
            
            // 4. 【最適化の核心】境界リストからランダムにターゲットを選ぶ
            Point targetPoint = allPerimeterPoints.get(rand.nextInt(allPerimeterPoints.size()));
            Site target = grid.getSite(targetPoint.x, targetPoint.y);
            int targetId = target.getId();

            // ターゲットの隣からソースをランダムに選択
            int[][] directions = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
            int[] dir = directions[rand.nextInt(4)];
            int sourceX = (target.getX() + dir[0] + grid.getWidth()) % grid.getWidth();
            int sourceY = (target.getY() + dir[1] + grid.getHeight()) % grid.getHeight();
            Site source = grid.getSite(sourceX, sourceY);
            int sourceId = source.getId();

            if (sourceId == targetId) {
                //continue;
            	return;
            }

            // 連結性チェック (CAアルゴリズム)
            if (!grid.checkLocalConnectivity(target.getX(), target.getY(), targetId)) {
            	return;
            }

            // エネルギー計算とMetropolis判定
            double d_energy = grid.computeDeltaEnergy(source, target);
            if (d_energy <= 0 || Math.exp(-d_energy / params.TEMPERATURE) > rand.nextDouble()) {
                grid.updateLocally(source, target);
                num_trans++;
                // 注意：ここで境界リストが変化するが、このMCSでは古いリストを使い続ける（下記参照）
            }
    }

    public void ticks() {
    }

	/**
	 * getGrid() メソッド
	 * 現在のグリッド状態を返します。
	 * @return 現在のGridオブジェクト
	 */
    @Override
	public Grid getGrid() {
		return grid;
	}
    @Override
	public double getNumSteps() {
		return num_steps;
	}
	public int getNumTrans() {
		return num_trans;
	}
}