import com.aparapi.Kernel;
import com.aparapi.Range;
import java.awt.Point;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Aparapiを使用してGPUでモンテカルロシミュレーションを実行するクラス。
 * 更新案の生成をCPUで、エネルギー計算と連結性チェックをGPUで並列処理するハイブリッドモデル。
 */
public class SimulatorGPUParrarel implements ISimulator{
	SimulationParams params;
	
    private final Grid grid;
    private final Random rand;
    private double num_steps = 0.0;
    private int num_trans = 0;

    public SimulatorGPUParrarel(SimulationParams p, int width, int height) {
    	params = p;
    	
        grid = new Grid(params, width, height);
        this.rand = new Random(params.RAND_SEED);
    }

 // SimulatorAparapi.java の中の MonteCarloKernel クラスを以下に差し替えてください

    private static class MonteCarloKernel extends Kernel {

        // --- フィールド群 (コンストラクタは変更なし) ---
        private final int[] siteIds;
        private final int width;
        private final int height;
        private final float maxDist;
        private final int[] cellAreas;
        private final int[] cellPerimeters;
        private final int[] cellTypes;
        private final int[] proposalSourceX;
        private final int[] proposalSourceY;
        private final int[] proposalTargetX;
        private final int[] proposalTargetY;
        private final float K_AREA;
        private final float TARGET_AREA;
        private final float K_PERI;
        private final float TARGET_PERIMETER;
        private final int[] J_matrix;
        private final int J_dim;
        private final float[] deltaEnergies;
        
        // ★★★ 修正点1: ローカル配列をfinalフィールドに移動 ★★★
        // これにより、GPU実行中の配列生成(new)を完全に排除する
        private final int[] dirX = {-1, 1, 0, 0}; // W, E, N, S
        private final int[] dirY = {0, 0, -1, 1};

        // コンストラクタは変更なし
        public MonteCarloKernel(SimulationParams params, Grid grid, List<Grid.UpdateProposal> proposals) {
            this.width = grid.getWidth();
            this.height = grid.getHeight();
            this.maxDist = (float)grid.getMaxDist();
            K_AREA = (float) params.K_AREA;
            TARGET_AREA = (float) params.TARGET_AREA;
            K_PERI = (float) params.K_PERI;
            TARGET_PERIMETER = (float) params.TARGET_PERIMETER;
            J_matrix = new int[params.J.length * params.J[0].length];
            J_dim = params.J.length;
            int proposalCount = proposals.size();
            this.siteIds = new int[width * height];
            for (int i = 0; i < width * height; i++) { this.siteIds[i] = grid.getSite(i % width, i / width).getId(); }
            int maxCellId = params.NUM_CELLS;
            this.cellAreas = new int[maxCellId + 1];
            this.cellPerimeters = new int[maxCellId + 1];
            this.cellTypes = new int[maxCellId + 1];
            
            int[] srcAreas = grid.getCellAreas(); 
            int[] srcPerimeters = grid.getCellPerimeters();
            for(int id=0; id<maxCellId; id++) {
            	this.cellAreas[id] = srcAreas[id];
            	this.cellPerimeters[id] = srcPerimeters[id];
            }
            for (int i = 0; i <= maxCellId; i++) { this.cellTypes[i] = (i == 0) ? 0 : 1; }
            for (int i = 0; i < J_dim; i++) { for (int j = 0; j < J_dim; j++) { this.J_matrix[i * J_dim + j] = params.J[i][j]; } }
            this.proposalSourceX = new int[proposalCount]; this.proposalSourceY = new int[proposalCount];
            this.proposalTargetX = new int[proposalCount]; this.proposalTargetY = new int[proposalCount];
            for (int i = 0; i < proposalCount; i++) {
                Grid.UpdateProposal p = proposals.get(i);
                this.proposalSourceX[i] = p.source().getX(); this.proposalSourceY[i] = p.source().getY();
                this.proposalTargetX[i] = p.target().getX(); this.proposalTargetY[i] = p.target().getY();
            }
            this.deltaEnergies = new float[proposalCount];
        }
        
        // --- ヘルパー関数 ---
        private int getSiteId(int x, int y) { return siteIds[y * width + x]; }
        private float sq(float x) { return x * x; }

        // ★★★ 修正点2: 複雑な分岐を徹底的に単純化 (ループ展開) ★★★
        private boolean checkLocalConnectivityGPU(int x, int y, int cellId) {
            if (cellId == 0) return true;

            // ループを完全に展開し、単純なフラグ変数で近傍の状態を調べる
            int z = 0;
            boolean hasW = false;
            boolean hasE = false;
            boolean hasN = false;
            boolean hasS = false;
            
            // West
            if (getSiteId((x - 1 + width) % width, y) == cellId) { z++; hasW = true; }
            // East
            if (getSiteId((x + 1 + width) % width, y) == cellId) { z++; hasE = true; }
            // North
            if (getSiteId(x, (y - 1 + height) % height) == cellId) { z++; hasN = true; }
            // South
            if (getSiteId(x, (y + 1 + height) % height) == cellId) { z++; hasS = true; }

            if (z == 0 || z == 4) return false;
            if (z == 1) return true;

            if (z == 2) {
                if (hasN && hasW) return getSiteId((x - 1 + width) % width, (y - 1 + height) % height) == cellId;
                if (hasN && hasE) return getSiteId((x + 1 + width) % width, (y - 1 + height) % height) == cellId;
                if (hasS && hasW) return getSiteId((x - 1 + width) % width, (y + 1 + height) % height) == cellId;
                if (hasS && hasE) return getSiteId((x + 1 + width) % width, (y + 1 + height) % height) == cellId;
                return false; // 対角でない場合
            }

            if (z == 3) {
                if (!hasE) return getSiteId((x - 1 + width) % width, (y - 1 + height) % height) == cellId && getSiteId((x - 1 + width) % width, (y + 1 + height) % height) == cellId;
                if (!hasW) return getSiteId((x + 1 + width) % width, (y - 1 + height) % height) == cellId && getSiteId((x + 1 + width) % width, (y + 1 + height) % height) == cellId;
                if (!hasS) return getSiteId((x - 1 + width) % width, (y - 1 + height) % height) == cellId && getSiteId((x + 1 + width) % width, (y - 1 + height) % height) == cellId;
                if (!hasN) return getSiteId((x - 1 + width) % width, (y + 1 + height) % height) == cellId && getSiteId((x + 1 + width) % width, (y + 1 + height) % height) == cellId;
            }
            return false;
        }

        // ★★★ 修正点3: ループと三項演算子を排除 ★★★
        private float computeDeltaEnergyGPU(int sID, int tID, int sx, int sy, int tx, int ty) {
            float dE = 0;

            if (sID > 0) dE += K_AREA * (sq(cellAreas[sID] + 1 - TARGET_AREA) - sq(cellAreas[sID] - TARGET_AREA));
            if (tID > 0) dE += K_AREA * (sq(cellAreas[tID] - 1 - TARGET_AREA) - sq(cellAreas[tID] - TARGET_AREA));

            int dLs = 0, dLt = 0, deltaAdhesionEnergy = 0;
            int sType = cellTypes[sID];
            int tType = cellTypes[tID];
            
            // 4方向のループを完全に展開し、三項演算子をif文に置き換える
            // Neighbor 1: West
            int nxW = (tx + dirX[0] + width) % width; int nyW = (ty + dirY[0] + height) % height;
            int nID_W = getSiteId(nxW, nyW); int nType_W = cellTypes[nID_W];
            if (nID_W == sID) dLs--; else dLs++;
            if (nID_W == tID) dLt++; else dLt--;
            if (tID != nID_W) deltaAdhesionEnergy -= J_matrix[tType * J_dim + nType_W];
            if (sID != nID_W) deltaAdhesionEnergy += J_matrix[sType * J_dim + nType_W];
            
            // Neighbor 2: East
            int nxE = (tx + dirX[1] + width) % width; int nyE = (ty + dirY[1] + height) % height;
            int nID_E = getSiteId(nxE, nyE); int nType_E = cellTypes[nID_E];
            if (nID_E == sID) dLs--; else dLs++;
            if (nID_E == tID) dLt++; else dLt--;
            if (tID != nID_E) deltaAdhesionEnergy -= J_matrix[tType * J_dim + nType_E];
            if (sID != nID_E) deltaAdhesionEnergy += J_matrix[sType * J_dim + nType_E];

            // Neighbor 3: North
            int nxN = (tx + dirX[2] + width) % width; int nyN = (ty + dirY[2] + height) % height;
            int nID_N = getSiteId(nxN, nyN); int nType_N = cellTypes[nID_N];
            if (nID_N == sID) dLs--; else dLs++;
            if (nID_N == tID) dLt++; else dLt--;
            if (tID != nID_N) deltaAdhesionEnergy -= J_matrix[tType * J_dim + nType_N];
            if (sID != nID_N) deltaAdhesionEnergy += J_matrix[sType * J_dim + nType_N];

            // Neighbor 4: South
            int nxS = (tx + dirX[3] + width) % width; int nyS = (ty + dirY[3] + height) % height;
            int nID_S = getSiteId(nxS, nyS); int nType_S = cellTypes[nID_S];
            if (nID_S == sID) dLs--; else dLs++;
            if (nID_S == tID) dLt++; else dLt--;
            if (tID != nID_S) deltaAdhesionEnergy -= J_matrix[tType * J_dim + nType_S];
            if (sID != nID_S) deltaAdhesionEnergy += J_matrix[sType * J_dim + nType_S];

            if (sID > 0) dE += K_PERI * (sq(cellPerimeters[sID] + dLs - TARGET_PERIMETER) - sq(cellPerimeters[sID] - TARGET_PERIMETER));
            if (tID > 0) dE += K_PERI * (sq(cellPerimeters[tID] + dLt - TARGET_PERIMETER) - sq(cellPerimeters[tID] - TARGET_PERIMETER));
            dE -= deltaAdhesionEnergy;
            
            // =========================================================
            // TODO: [引き継ぎ用] GPU計算での新しいエネルギー項の追加場所
            // =========================================================
            // 【重要】Aparapiの制約により、GPUカーネル内（ここ）では以下の操作が禁止されています。
            // × new によるオブジェクト生成（PointやListなど）
            // × 外部の複雑なメソッド呼び出し
            // 追加の計算を行う場合は、事前に1次元配列としてデータをGPUメモリに転送しておき、
            // プリミティブ型（int, float）のみを用いて dE に加算してください。
            
            return dE;
        }
        
        // run() メソッドは前回の修正版（早期リターン型）のままでOK
        @Override
        public void run() {
            int gid = getGlobalId();
            int sx = proposalSourceX[gid]; int sy = proposalSourceY[gid];
            int tx = proposalTargetX[gid]; int ty = proposalTargetY[gid];
            int sID = getSiteId(sx, sy); int tID = getSiteId(tx, ty);

            if (!checkLocalConnectivityGPU(tx, ty, tID)) {
                deltaEnergies[gid] = Float.POSITIVE_INFINITY;
                return;
            }
            
            float dE = computeDeltaEnergyGPU(sID, tID, sx, sy, tx, ty);
            deltaEnergies[gid] = dE;
        }
        
        public float[] getDeltaEnergies() {
            return deltaEnergies;
        }
    }

    @Override
    public void runMonteCarloStep() {
    	runMonteCarloStepWithGPU();
    }
    
    @Override
    public void ticks() {
    }
    
    /**
     * GPUを使用して1モンテカルロステップ（MCS）分シミュレーションを進めます。
     */
    private void runMonteCarloStepWithGPU() {
        // フェーズ1: CPUによる更新案の生成 (変更なし)
        List<Grid.UpdateProposal> proposals = generateProposalsOnCPU();
        if (proposals.isEmpty()) return;

        // フェーズ2: GPUによる差分エネルギーの並列計算
        MonteCarloKernel kernel = new MonteCarloKernel(params, this.grid, proposals);
        float[] deltaEnergies;
        try {
            kernel.setExecutionMode(Kernel.EXECUTION_MODE.GPU);
            //System.out.println("Execution mode set to: " + kernel.getExecutionMode());

            Range range = Range.create(proposals.size());
            //Range range = Range.create(1);
            kernel.execute(range);
            
            // ★★★ 変更点5: GPUからの結果取得 ★★★
            deltaEnergies = kernel.getDeltaEnergies();

        } catch (Exception e) {
            System.err.println("!!! Aparapiカーネルの実行中にエラーが発生しました !!!");
            e.printStackTrace();
            return;
        } 
        kernel.dispose(); // GPUリソースを解放
        
        // ======================= フェーズ3: CPUによる受理判定と結果の適用 =======================
        List<Grid.UpdateProposal> acceptedProposals = new ArrayList<>();
        for (int i = 0; i < proposals.size(); i++) {
            float dE = deltaEnergies[i];

            // GPUが計算したΔEが無限大の場合、連結性チェックに失敗したためスキップ
            if (Float.isInfinite(dE)) {
                continue;
            }
            
            // ★★★ 変更点6: 受理判定(メトロポリス法)をCPUで実行 ★★★
            if (dE <= 0 || Math.exp(-dE / params.TEMPERATURE) > rand.nextFloat()) {
                 acceptedProposals.add(proposals.get(i));
            }
        }

        // 競合を避けながらグリッドを更新 (変更なし)
        applyProposalsOnCPU(acceptedProposals);
    }
    
    private List<Grid.UpdateProposal> generateProposalsOnCPU() {
        List<Grid.UpdateProposal> proposals = new ArrayList<>();
        List<Point> allPerimeterPoints = new ArrayList<>();
        Set<Point>[] perimeterMap = grid.getCellPerimeterCoords();
        
        for (Set<Point> points : perimeterMap) {
            if (points != null && !points.isEmpty()) {
                allPerimeterPoints.addAll(points);
            }
        }
        if (allPerimeterPoints.isEmpty()) return proposals;
        int totalAttempts = allPerimeterPoints.size();
        //int totalAttempts = 20;
        num_steps += (double)(totalAttempts)/allPerimeterPoints.size();
        int[] dx = {0, 0, 1, -1}, dy = {1, -1, 0, 0};
        for (int i = 0; i < totalAttempts; i++) {
            Point targetPoint = allPerimeterPoints.get(rand.nextInt(allPerimeterPoints.size()));
            Site target = grid.getSite(targetPoint.x, targetPoint.y);
            int dirIndex = rand.nextInt(4);
            int sx = (targetPoint.x + dx[dirIndex] + grid.getWidth()) % grid.getWidth();
            int sy = (targetPoint.y + dy[dirIndex] + grid.getHeight()) % grid.getHeight();
            Site source = grid.getSite(sx, sy);
            if (source.getId() != target.getId()) { proposals.add(new Grid.UpdateProposal(source, target)); }
        }
        return proposals;
    }
    
    private void applyProposalsOnCPU(List<Grid.UpdateProposal> acceptedProposals) { /* ... 前回のコードと同じ ... */ 
        Collections.shuffle(acceptedProposals, rand);
        Set<Point> updatedPoints = new HashSet<>();
        for (Grid.UpdateProposal proposal : acceptedProposals) {
            Point sourcePoint = new Point(proposal.source().getX(), proposal.source().getY());
            Point targetPoint = new Point(proposal.target().getX(), proposal.target().getY());
            if ((updatedPoints.contains(sourcePoint) || updatedPoints.contains(targetPoint))) { continue; }
            if (!grid.checkLocalConnectivity((int)targetPoint.getX(), (int)targetPoint.getY(), grid.getSite((int)targetPoint.getX(), (int)targetPoint.getY()).getId())) continue;
            grid.updateLocally(proposal.source(), proposal.target());
            //updatedPoints.add(sourcePoint);
            updatedPoints.add(targetPoint);
            num_trans++;
        }
    }

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