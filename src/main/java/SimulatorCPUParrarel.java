import java.awt.Point;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set; // Setをインポート
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Callable;

/**
 * Simulatorクラスは、シミュレーションの更新（セルの動きなど）を管理します。
 * Gridクラスの状態を元に、ランダムなセル更新処理を行い、エネルギー変化に基づいてその変更を確率的に受け入れます。
 */
class SimulatorCPUParrarel {
	private SimulationParams params;
	
	private Grid grid;      // シミュレーション対象のグリッド（場）のオブジェクト
	private Random rand;    // ランダムな数値生成用のオブジェクト。更新時のランダム選択に利用します。

	/**
	 * コンストラクタ
	 * 指定された幅と高さのグリッドを初期化し、ランダムオブジェクトも生成します。
	 * @param width グリッドの横幅（セル数）
	 * @param height グリッドの縦幅（セル数）
	 */
	public SimulatorCPUParrarel(SimulationParams p, int width, int height) {
		params = p;
		grid = new Grid(p, width, height); // 指定サイズのグリッドを生成
		rand = new Random(params.RAND_SEED);        // ランダムな更新処理のためのオブジェクトを生成
	}
	
	/**
     * 1つのスレッドで実行されるモンテカルロ法のタスク。
     * 更新案のリストを生成して返す。Gridの変更は行わない。
     */
	private class MonteCarloTask implements Callable<List<Grid.UpdateProposal>> {
	    private final Grid readOnlyGrid;
	    private final List<Point> assignedPerimeterSubset; // ★ 割り当てられた部分リスト
	    private final Random localRand;

	    // ★ コンストラクタを修正
	    public MonteCarloTask(Grid grid, List<Point> assignedPerimeterSubset) {
	        this.readOnlyGrid = grid;
	        this.assignedPerimeterSubset = assignedPerimeterSubset;
	        this.localRand = new Random();
	    }

	    @Override
	    public List<Grid.UpdateProposal> call() throws Exception {
	        List<Grid.UpdateProposal> proposals = new ArrayList<>();
	        if (assignedPerimeterSubset.isEmpty()) {
	            return proposals;
	        }

	        // ★ 論文のStep 3 & 4: 担当リストからターゲットを1つ、その隣からソースを1つ選ぶ
	        // このタスクでは1試行のみを行う
	        Point targetPoint = assignedPerimeterSubset.get(localRand.nextInt(assignedPerimeterSubset.size()));
	        Site target = readOnlyGrid.getSite(targetPoint.x, targetPoint.y);
	        
	        // ... (以降のロジックは同じ。ただし、ループは不要) ...
	        int[][] directions = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
	        int[] dir = directions[localRand.nextInt(4)];
	        int sourceX = (target.getX() + dir[0] + readOnlyGrid.getWidth()) % readOnlyGrid.getWidth();
	        int sourceY = (target.getY() + dir[1] + readOnlyGrid.getHeight()) % readOnlyGrid.getHeight();
	        Site source = readOnlyGrid.getSite(sourceX, sourceY);
	        
	        if (source.getId() == target.getId()) return proposals;

	        if (!readOnlyGrid.checkLocalConnectivity(target.getX(), target.getY(), target.getId())) return proposals;
	        //if (!readOnlyGrid.checkLocalConnectivity(target.getX(), target.getY(), source.getId())) return proposals;

	        double d_energy = readOnlyGrid.computeDeltaEnergy(source, target);
	        if (d_energy <= 0 || Math.exp(-d_energy / params.TEMPERATURE) > localRand.nextDouble()) {
	            proposals.add(new Grid.UpdateProposal(source, target));
	        }
	        
	        return proposals;
	    }
	}
	
	/**
     * 境界リストとExecutorServiceを用いて並列化し、1モンテカルロステップ（MCS）分シミュレーションを進めます。
     * @param executorService 並列実行に使用するExecutorService
     */
    public void runMonteCarloStep(ExecutorService executorService) throws Exception {
        // 1. 更新候補となる境界リストを準備する
        List<Point> allPerimeterPoints = new ArrayList<>();
        Set<java.awt.Point>[] perimeterMap = grid.getCellPerimeterCoords();
        
        for (Set<Point> points : perimeterMap) {
            if (points != null && !points.isEmpty()) {
                allPerimeterPoints.addAll(points);
            }
        }
        if (allPerimeterPoints.isEmpty()) return;

        // ======================= フェーズ1: 更新案の並列生成 =======================
        int numThreads = Runtime.getRuntime().availableProcessors(); // 利用可能なCPUコア数を取得

        List<Callable<List<Grid.UpdateProposal>>> tasks = new ArrayList<>();
        for (int i = 0; i < numThreads; i++) {
            tasks.add(new MonteCarloTask(this.grid, allPerimeterPoints));
        }

        // 全てのタスクを投入し、完了を待つ
        List<Future<List<Grid.UpdateProposal>>> futures = executorService.invokeAll(tasks);

        // 全てのタスクの結果（更新案リスト）を一つにまとめる
        List<Grid.UpdateProposal> allProposals = new ArrayList<>();
        for (Future<List<Grid.UpdateProposal>> future : futures) {
            allProposals.addAll(future.get());
        }

        // ======================= フェーズ2: 更新の逐次適用 =======================
        // 競合を避けるため、更新が適用されたピクセルを記録する
        Set<Site> updatedPoints = new HashSet<>();

        // 提案をシャッフルして、特定の領域に偏って更新されるのを防ぐ
        java.util.Collections.shuffle(allProposals, rand);

        for (Grid.UpdateProposal proposal : allProposals) {
            Site sourceSite = new Site(proposal.source().getId(), proposal.source().getX(), proposal.source().getY());
            Site targetSite = new Site(proposal.target().getId(), proposal.target().getX(), proposal.target().getY());
            
            // 競合チェック：sourceまたはtargetが既にこのステップで更新されていたらスキップ
            if (updatedPoints.contains(sourceSite) || updatedPoints.contains(targetSite)) {
                continue;
            }
            
            if(!grid.checkLocalConnectivity(targetSite.getX(), targetSite.getY(), targetSite.getId())) {
            	continue;
            }

            // 競合がないので、Gridを実際に更新
            grid.updateLocally(proposal.source(), proposal.target());
            
            // 更新したピクセルを記録
            updatedPoints.add(sourceSite);
            updatedPoints.add(targetSite);
        }
    }


	/**
	 * getGrid() メソッド
	 * 現在のグリッド状態を返します。
	 * @return 現在のGridオブジェクト
	 */
	public Grid getGrid() {
		return grid;
	}
}