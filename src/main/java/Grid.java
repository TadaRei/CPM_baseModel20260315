import java.awt.Point;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

/**
 * シミュレーションの「場」となる2次元グリッドを表現します。
 * Cellular Potts Model (CPM) に基づき、細胞の位置、形状、エネルギー状態などを管理します。
 * 効率的な局所更新機能を提供し、シミュレーションの各ステップを実行します。
 */
class Grid {
	/**
	 * 細胞配置の初期化のパターン
	 * 0: 中心に細胞を設置（セル一つの場合に対応）
	 * 1: 指定の円内に一様分布で細胞を設置
	 * 2: 指定の円内にガウス分布で細胞を設置
	 * 3: 場の全般に一様分布で細胞を設置
	 */
	private int INITIALIZE_PATTERN = 2;
	
    // --- フィールド（クラスが保持する変数） ---
	/** パラメータクラスの変数 */
	private SimulationParams params;

    /** 細胞の初期配置などで使用する乱数生成器 */
    private final Random rand;
    
    /** Pointオブジェクトを座標に基づいてソートするための比較器（Comparator） */
    private final Comparator<java.awt.Point> pointComparator = 
        Comparator.comparingInt((java.awt.Point p) -> p.x)
                  .thenComparingInt((java.awt.Point p) -> p.y);
    
    /** グリッドの幅（ピクセル数） */
    private int width;
    /** グリッドの高さ（ピクセル数） */
    private int height;
    /** グリッド上の各ピクセル（Site）の状態を格納する2次元配列 */
    private Site[] sites;
    /** グリッド上において円形に細胞を配置する時の半径 */
    private double maxDist;
    
    /** 遷移回数 */
    private int numTrans = 0;
    
    /** 各細胞の面積、周囲長、細胞間接着、細胞タイプの格納先 */
    private int[] cellAreas;
    private int[] cellPerimeters;
    private int[] cellContactEnergy;
    private int[] cellTypes;
    /** グリッドの遷移可能な位置を記録するバウンダリーリスト */
    private Set<Point>[] cellPerimeterCoords;
    
    /** グリッド全体の現在の総エネルギー */
    private int energy;

    /**
     * グリッドを生成し、細胞を初期配置するメインコンストラクタ
     * @param width グリッドの幅
     * @param height グリッドの高さ
     */
    public Grid(SimulationParams p, int width, int height) {
    	params = p;
    	rand = new Random(params.RAND_SEED);
    	
        this.width = width;
        this.height = height;
        this.sites = new Site[width*height];
        this.maxDist = Math.min(width, height);
        
        int numCells = params.NUM_CELLS;
        this.cellAreas = new int[numCells];
        this.cellPerimeters = new int[numCells];
        this.cellContactEnergy = new int[numCells];
        this.cellTypes = new int[numCells];
        this.cellPerimeterCoords = new TreeSet[numCells];
        
        for(int i=0; i<numCells; i++) {
        	this.cellPerimeterCoords[i] = new TreeSet<>(pointComparator);
        }
        
        
        initializeGrid();
        updateCellProperties(); // 全プロパティを初期計算
        this.energy = calculateEnergy();
    }

    /**
     * グリッドのクローンを効率的に作成するための、初期化処理をスキップする軽量コンストラクタ
     */
    private Grid(SimulationParams p, int width, int height, boolean isClone) {
    	params = p;
    	rand = new Random(params.RAND_SEED);
    	
        this.width = width;
        this.height = height;
        this.sites = new Site[width*height];
        this.maxDist = Math.min(width, height);

        int numCells = params.NUM_CELLS;
        this.cellAreas = new int[numCells];
        this.cellPerimeters = new int[numCells];
        this.cellContactEnergy = new int[numCells];
        this.cellTypes = new int[numCells];
        this.cellPerimeterCoords = new TreeSet[numCells];
        
        for(int i=0; i<numCells; i++) {
        	this.cellPerimeterCoords[i] = new TreeSet<>(pointComparator);
        }
        
    }

    // =================================================================
    // ========== 刷新された局所更新ロジック ==========
    // =================================================================
    private record ChangeInfo(int deltaEnergyInfo, double deltaEnergyPlus, int dLs, int dLt) {}
    /** 更新案を保持するためのレコード */
    public record UpdateProposal(Site source, Site target) {}

    /** グリッドの遷移を計算する */
    private ChangeInfo calculateChangeDetails(Site source, Site target) {
        int sID = source.getId();
        int tID = target.getId();
        int tx = target.getX();
        int ty = target.getY();
        int dE = 0;

        // 1. 面積エネルギー項 ΔE_area の計算
        if (sID > 0) {
            int aOld = cellAreas[sID];
            dE += params.K_AREA * (sq(aOld + 1 - params.TARGET_AREA) - sq(aOld - params.TARGET_AREA));
        }
        if (tID > 0) {
            int aOld = cellAreas[tID];
            dE += params.K_AREA * (sq(aOld - 1 - params.TARGET_AREA) - sq(aOld - params.TARGET_AREA));
        }

        // 2. 周囲長 & 接着エネルギー項の計算
        int dLs = 0;
        int dLt = 0;
        int deltaAdhesionEnergy = 0;
        int sType = cellTypes[sID];
        int tType = cellTypes[tID];

        for (Point neighbor : getNeighbors(new Point(tx, ty), false)) {
            int nID = getSite(neighbor.x, neighbor.y).getId();
            int nType = cellTypes[nID];
            
            dLs += (nID == sID) ? -1 : 1;
            dLt += (nID == tID) ? 1 : -1;

            if (tID != nID) {
                deltaAdhesionEnergy -= params.J[tType][nType];
            }
            if (sID != nID) {
                deltaAdhesionEnergy += params.J[sType][nType];
            }
        }
        
        // 周囲長エネルギーの差分を合計に追加
        if (sID > 0) {
            int lOld = cellPerimeters[sID];
            dE += params.K_PERI * (sq(lOld + dLs - params.TARGET_PERIMETER) - sq(lOld - params.TARGET_PERIMETER));
        }
        if (tID > 0) {
            int lOld = cellPerimeters[tID];
            dE += params.K_PERI * (sq(lOld + dLt - params.TARGET_PERIMETER) - sq(lOld - params.TARGET_PERIMETER));
        }

        dE -= deltaAdhesionEnergy;
        
     // =========================================================
        // TODO: [引き継ぎ用] 新しいエネルギー項（走化性や体積制約など）を追加する場所
        // =========================================================
        // params クラスに新しく定義した係数（K_CHEMOなど）を用いて差分エネルギーを計算し、dEに加算・減算してください。
        // 例: dE += params.K_CHEMO * (targetChemo - sourceChemo);
        
        double dEPlus = dE;
        // ※ dEPlus には最終的な遷移前後のエネルギー差を代入して返します。
        
        return new ChangeInfo(dE, dEPlus, dLs, dLt);
    }

    public double computeDeltaEnergy(Site source, Site target) {
        if (source.getId() == target.getId()) return 0;
        return calculateChangeDetails(source, target).deltaEnergyPlus;
    }
    
    public void updateLocally(Site source, Site target) {
        int sID = source.getId();
        int tID = target.getId();
        if (sID == tID) return;
        
        ChangeInfo info = calculateChangeDetails(source, target);

        Point targetPoint = new Point(target.getX(), target.getY());
        List<Point> affectedPoints = getNeighbors(targetPoint, false);
        affectedPoints.add(targetPoint);
        Map<Point, Boolean> wasBoundary = new HashMap<>();
        for (Point p : affectedPoints) {
            wasBoundary.put(p, isBoundary(p.x, p.y));
        }

        updateContactEnergy(sID, tID, targetPoint);
        updateArea(sID, tID);
        updatePerimeter(sID, tID, info.dLs, info.dLt);
        setCellId(target.getX(), target.getY(), sID);
        updatePerimeterCoordinates(sID, tID, targetPoint, affectedPoints, wasBoundary);
        
        numTrans++;
        this.energy += info.deltaEnergyInfo;
    }

    private void updateArea(int sID, int tID) {
        if (sID > 0) {
            cellAreas[sID] = cellAreas[sID] + 1;
        }
        if (tID > 0) {
            int newArea = cellAreas[tID] - 1;
            if (newArea > 0) {
                cellAreas[tID] = newArea;
            }
        }
    }

    private void updatePerimeter(int sID, int tID, int dLs, int dLt) {
        if (sID > 0) {           
            cellPerimeters[sID] = cellPerimeters[sID] + dLs;
        }
        if (tID > 0 && cellPerimeters[tID] != 0) {
            cellPerimeters[tID] = cellPerimeters[tID] + dLt;
        }
    }
    
    private void updateContactEnergy(int sID, int tID, Point targetPoint) {
        int sType = cellTypes[sID];
        int tType = cellTypes[tID];
        
        for (Point neighbor : getNeighbors(targetPoint, false)) {
        	int sDeltaE = 0;
            int tDeltaE = 0;
            int nDeltaE = 0;
            
            int nID = getSite(neighbor.x, neighbor.y).getId();
            int nType = cellTypes[nID];

            if (nID == sID) {
            	sDeltaE -= params.J[sType][tType];
            	tDeltaE -= params.J[tType][sType];
            } else if (nID == tID) {
            	sDeltaE += params.J[sType][tType];
            	tDeltaE += params.J[tType][sType];
            } else {
                int delta_n = params.J[nType][sType] - params.J[nType][tType];
                nDeltaE += delta_n;
                tDeltaE -= params.J[tType][nType];
                sDeltaE += params.J[sType][nType];
            }
            if(nID > 0) cellContactEnergy[nID] += nDeltaE;
            if(tID > 0) cellContactEnergy[tID] += tDeltaE;
            if(sID > 0) cellContactEnergy[sID] += sDeltaE;
        }
    }

    private void updatePerimeterCoordinates(int sID, int tID, Point targetPoint, List<Point> affectedPoints, Map<Point, Boolean> wasBoundary) {
        for (Point p : affectedPoints) {
            boolean isNowBoundary = isBoundary(p.x, p.y);
            boolean wasPreviouslyBoundary = wasBoundary.get(p);

            if (wasPreviouslyBoundary) {
                int oldId = p.equals(targetPoint) ? tID : getSite(p.x, p.y).getId();
                Set<java.awt.Point> oldCoords = cellPerimeterCoords[oldId];
                if (oldCoords != null) {
                    oldCoords.remove(p);
                }
            }
            if (isNowBoundary) {
                int currentId = getSite(p.x, p.y).getId();
                if(cellPerimeterCoords == null) {
                	cellPerimeterCoords[currentId] = new TreeSet<>(pointComparator);
                }
                cellPerimeterCoords[currentId].add(p);
            }
        }
    }

    // =================================================================
    // ========== 以下はその他のメソッド群 ==========
    // =================================================================
    
    /**
     * 論文で提案された効率的な局所連結性テストを実装します。
     * サイト(x, y)の値を変更した場合に、cellIdが連結性を維持できるかを
     * そのサイトの近傍のみを調べて高速に判定します。
     * @param x チェックするサイトのx座標
     * @param y チェックするサイトのy座標
     * @param cellId 連結性をチェックする対象の細胞ID
     * @return 連結性が維持される場合はtrue、そうでない場合はfalse
     */
    public boolean checkLocalConnectivity(int x, int y, int cellId) {
        // 背景（ID=0）は常に連結していると見なす
        if (cellId == 0) return true;

        // 1. 隣接近傍（フォン・ノイマン近傍：上下左右）の情報を取得
        // (dx, dy, direction_name)
        int[][] adjCoords = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}}; // W, E, N, S (x, y)
        String[] directions = {"W", "E", "N", "S"};
        List<String> neighborDirections = new ArrayList<>();

        for (int i = 0; i < adjCoords.length; i++) {
            // 周期境界条件を考慮
            int nx = (x + adjCoords[i][0] + width) % width;
            int ny = (y + adjCoords[i][1] + height) % height;
            if (sites[ny*width+nx].getId() == cellId) {
                neighborDirections.add(directions[i]);
            }
        }

        int z = neighborDirections.size();

        // 2. 論文のルールに基づいて判定
        if (z == 0 || z == 4) {
            return false; // 連結していないか、完全に囲まれていて変更不可
        }
        if (z == 1) {
            return true; // 常に連結
        }

        // 3. z=2 の場合：コーナーで繋がっているかチェック (論文 図7a)
        if (z == 2) {
            boolean isConnected = false;
            if (neighborDirections.contains("N") && neighborDirections.contains("W")) {
                // 北西のサイトも同じcellIdか？
            	int nx_nw = (x - 1 + width) % width;
                int ny_nw = (y - 1 + height) % height;
                isConnected = (sites[ny_nw*width + nx_nw].getId() == cellId);
            } else if (neighborDirections.contains("N") && neighborDirections.contains("E")) {
                // 北東のサイト
            	int nx_ne = (x + 1 + width) % width;
                int ny_ne = (y - 1 + height) % height;
                isConnected = (sites[ny_ne*width + nx_ne].getId() == cellId);
            } else if (neighborDirections.contains("S") && neighborDirections.contains("W")) {
                // 南西のサイト
            	int nx_sw = (x - 1 + width) % width;
            	int ny_sw = (y + 1 + height) % height;
                isConnected = (sites[ny_sw*width + nx_sw].getId() == cellId);
            } else if (neighborDirections.contains("S") && neighborDirections.contains("E")) {
                // 南東のサイト
            	int nx_se = (x + 1 + width) % width;
                int ny_se = (y + 1 + height) % height;
                isConnected = (sites[ny_se*width + nx_se].getId() == cellId);
            }
            return isConnected;
        }

        // 4. z=3 の場合：L字型で繋がっているかチェック (論文 図7b)
        if (z == 3) {
            boolean isConnected = false;
            if (!neighborDirections.contains("E")) { // N, S, W の場合
            	int nx_nw = (x - 1 + width) % width;
                int ny_nw = (y - 1 + height) % height;
            	int nx_sw = (x - 1 + width) % width;
            	int ny_sw = (y + 1 + height) % height;
            	isConnected = (sites[ny_nw*width + nx_nw].getId() == cellId &&
            			sites[ny_sw*width + nx_sw].getId() == cellId);
            } else if (!neighborDirections.contains("W")) { // N, S, E の場合
            	int nx_ne = (x + 1 + width) % width;
                int ny_ne = (y - 1 + height) % height;
            	int nx_se = (x + 1 + width) % width;
            	int ny_se = (y + 1 + height) % height;
            	isConnected = (sites[ny_ne*width + nx_ne].getId() == cellId &&
            			sites[ny_se*width + nx_se].getId() == cellId);
            } else if (!neighborDirections.contains("S")) { // N, W, E の場合
            	int nx_nw = (x - 1 + width) % width;
            	int ny_nw = (y - 1 + height) % height;
            	int nx_ne = (x + 1 + width) % width;
                int ny_ne = (y - 1 + height) % height;
            	isConnected = (sites[ny_nw*width + nx_nw].getId() == cellId &&
            			sites[ny_ne*width + nx_ne].getId() == cellId);
            } else if (!neighborDirections.contains("N")) { // S, W, E の場合
            	int nx_sw = (x - 1 + width) % width;
            	int ny_sw = (y + 1 + height) % height;
            	int nx_se = (x + 1 + width) % width;
                int ny_se = (y + 1 + height) % height;
            	isConnected = (sites[ny_sw*width + nx_sw].getId() == cellId &&
            			sites[ny_se*width + nx_se].getId() == cellId);
            }
            return isConnected;
        }
        
        return false; // 上記以外は非連結
    }
    
    public Grid cloneGrid() {
        Grid newGrid = new Grid(this.params, this.width, this.height, true);
        newGrid.copyFrom(this);
        return newGrid;
    }

    public void copyFrom(Grid source) {
        this.width = source.width;
        this.height = source.height;
        this.energy = source.energy;

        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {
                this.sites[j*width+i] = new Site(source.sites[j*width+i].getId(), i, j);
            }
        }

        int numCells = params.NUM_CELLS;
        this.cellAreas = Arrays.copyOf(source.cellAreas, numCells);
        this.cellPerimeters = Arrays.copyOf(source.cellPerimeters, numCells);
        this.cellContactEnergy = Arrays.copyOf(source.cellContactEnergy, numCells);
        this.cellTypes = Arrays.copyOf(source.cellTypes, numCells);

        for(int cellId=0; cellId<numCells; cellId++) {
        	Set<java.awt.Point> points = new TreeSet<>(pointComparator);
            for(Point entry:source.cellPerimeterCoords[cellId]) {
            	points.add(new Point(entry.x, entry.y));
            }
            this.cellPerimeterCoords[cellId] = points;
        }
    }
    
    public int calculateEnergy() {
        int areaEnergy = 0;
        int perimeterEnergy = 0;
        int adhesionEnergy = 0;
        int numCells = params.NUM_CELLS;

        for (int cellId=0; cellId < numCells; cellId++) {
            if (cellId == 0) continue;
            int area = cellAreas[cellId];
            areaEnergy += params.K_AREA * sq(area - params.TARGET_AREA);

            int perimeter = cellPerimeters[cellId];
            perimeterEnergy += params.K_PERI * sq(perimeter - params.TARGET_PERIMETER);
        }

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int id1 = sites[y*width+x].getId();
                int type1 = cellTypes[id1];
                if (x + 1 < width) {
                	int id2 = sites[y*width+(x + 1)].getId();
                    if (id1 != id2) adhesionEnergy += params.J[type1][cellTypes[id2]];
                }
                if (y + 1 < height) {
                	int id2 = sites[(y + 1)*width+x].getId();
                    if (id1 != id2) adhesionEnergy += params.J[type1][cellTypes[id2]];
                }
            }
        }
        
        return areaEnergy + perimeterEnergy - adhesionEnergy;
    }
    
    public void updateCellProperties() {        
        Arrays.fill(cellAreas, 0);
        Arrays.fill(cellPerimeters, 0);
        Arrays.fill(cellContactEnergy, 0);
        for(int i=0; i<cellPerimeterCoords.length; i++) {
        	cellPerimeterCoords[i].clear();
        }
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int id = sites[y*width+x].getId();
                cellAreas[id] = cellAreas[id] + 1;
                if (isBoundary(x, y)) {
                	if(cellPerimeterCoords[id] == null) {
                		cellPerimeterCoords[id] = new TreeSet<>(pointComparator);
                	}
                    cellPerimeterCoords[id].add(new Point(x, y));
                }
            }
        }

        int numCells = params.NUM_CELLS;
        
        for (int cellId=0; cellId < numCells; cellId++) {
            if (cellId == 0) continue;
            int perimeter = 0;
            int contactE = 0;
            int type1 = cellTypes[cellId];
            Set<java.awt.Point> boundaryPixels = cellPerimeterCoords[cellId];
            if (boundaryPixels != null) {
                for (Point p : boundaryPixels) {
                    for (Point neighbor : getNeighbors(p, false)) {
                        int neighborId = getSite(neighbor.x, neighbor.y).getId();
                        if (neighborId != cellId) {
                            perimeter++;
                            contactE += params.J[type1][cellTypes[neighborId]];
                        }
                    }
                }
            }
            cellPerimeters[cellId] = perimeter;
            cellContactEnergy[cellId] = contactE;
        }
    }

    private void initializeGrid() {
    	for (int j = 0; j < height; j++) { // y
            for (int i = 0; i < width; i++) { // x
                sites[j*width+i] = new Site(0, i, j); // (i=x, j=y)
            }
        }
    	
		final int centerX = width / 2;
        final int centerY = height / 2;
        final int radius = Math.min(width, height) / 5;
    	
    	switch(this.INITIALIZE_PATTERN) {
    	case 0:
    		if(params.NUM_CELLS > 1) System.out.println("細胞数が1よりも大きいです");
    		
    		for (int i = centerX - 1; i <= centerX + 1; i++) { // x
                for (int j = centerY - 1; j <= centerY + 1; j++) { // y
                    if (i >= 0 && i < width && j >= 0 && j < height && sites[j*width+i].getId() == 0) {
                        sites[j*width+i] = new Site(1, i, j);
                    }
                }
            }
            cellTypes[1] = 1;
    		
    		break;
    	case 1:

            for (int cellId = 1; cellId < params.NUM_CELLS; cellId++) {
                int cellX, cellY;
                while (true) {
                    int dx = rand.nextInt(2 * radius) - radius;
                    int dy = rand.nextInt(2 * radius) - radius;
                    if (dx * dx + dy * dy <= radius * radius) {
                        cellX = centerX + dx;
                        cellY = centerY + dy;
                        break;
                    }
                }
                for (int i = cellX - 1; i <= cellX + 1; i++) { // x
                    for (int j = cellY - 1; j <= cellY + 1; j++) { // y
                        if (i >= 0 && i < width && j >= 0 && j < height && sites[j*width+i].getId() == 0) {
                            sites[j*width+i] = new Site(cellId, i, j);
                        }
                    }
                }
                cellTypes[cellId] = 1;
            }
    		break;
    	case 2:
            for (int cellId = 1; cellId < params.NUM_CELLS; cellId++) {
                int cellX, cellY;
                while (true) {
                	
                    int dx = (int)rand.nextGaussian(0, radius);
                    int dy = (int)rand.nextGaussian(0, radius);
                    
                    if (dx * dx + dy * dy <= radius * radius) {
                        cellX = centerX + dx;
                        cellY = centerY + dy;
                        break;
                    }
                }
                for (int i = cellX - 1; i <= cellX + 1; i++) { // x
                    for (int j = cellY - 1; j <= cellY + 1; j++) { // y
                        if (i >= 0 && i < width && j >= 0 && j < height && sites[j*width+i].getId() == 0) {
                            sites[j*width+i] = new Site(cellId, i, j);
                        }
                    }
                }
                cellTypes[cellId] = 1;
            }
    		break;
    	case 3:
    		for (int cellId = 1; cellId < params.NUM_CELLS; cellId++) {
                int cellX = rand.nextInt(10, width-10);
                int cellY = rand.nextInt(10, height-10);
                
                for (int i = cellX - 1; i <= cellX + 1; i++) { // x
                    for (int j = cellY - 1; j <= cellY + 1; j++) { // y
                        if (i >= 0 && i < width && j >= 0 && j < height && sites[j*width+i].getId() == 0) {
                            sites[j*width+i] = new Site(cellId, i, j);
                        }
                    }
                }
                cellTypes[cellId] = 1;
            }
    		break;
    	}
    	
        
        cellTypes[0] = 0;
    }

    private boolean isBoundary(int x, int y) {
        int id = sites[y*width+x].getId();
        for (Point neighbor : getNeighbors(new Point(x, y), false)) {
            if (sites[neighbor.y*width+neighbor.x].getId() != id) return true;
        }
        return false;
    }

    private List<Point> getNeighbors(Point p, boolean periodic) {
        List<Point> neighbors = new ArrayList<>();
        int[] dx = {0, 0, 1, -1}, dy = {1, -1, 0, 0};
        for (int i = 0; i < 4; i++) {
            int nx = p.x + dx[i], ny = p.y + dy[i];
            if (periodic) {
                nx = (nx + width) % width;
                ny = (ny + height) % height;
                neighbors.add(new Point(nx, ny));
            } else if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                neighbors.add(new Point(nx, ny));
            }
        }
        return neighbors;
    }

    private static int sq(int x) { return x * x; }
    
    public Site[][] to2DArray(Site[] arr, int rows, int cols) {
        if (arr.length != rows * cols) {
            throw new IllegalArgumentException("配列の要素数が、指定された行数・列数と一致しません。");
        }

        Site[][] result = new Site[rows][cols];
        for (int i = 0; i < arr.length; i++) {
            // 1次元配列のインデックスから2次元の行・列を計算
            int row = i / cols;
            int col = i % cols;
            result[row][col] = arr[i];
        }
        return result;
    }
    
    public int[][] perimeterCoordsToInt() {
        int[][] perimeterCoords = new int[this.width][this.height];
        if (this.cellPerimeterCoords != null) {
            for (Set<java.awt.Point> points : this.cellPerimeterCoords) {
                for (Point p : points) {
                    perimeterCoords[p.x][p.y]++;
                }
            }
        }
        return perimeterCoords;
    }
    
    // --- ゲッター/セッター群 ---
    public Site getSite(int x, int y) { return sites[y*width+x]; }
    public Site[][] getGrid(){return to2DArray(sites, height, width);}
    public int[] getCellAreas() { return cellAreas; }
    public int[] getCellPerimeters() { return cellPerimeters; }
    public int[] getCellContactEnergy() { return cellContactEnergy; }
    public Set<Point>[] getCellPerimeterCoords() { return cellPerimeterCoords; }
    public void setCellId(int x, int y, int id) { sites[y*width+x] = new Site(id, x, y); }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public double getMaxDist() {return maxDist;}
    public int getEnergy() { return energy; }
    public int getNumTrans() {return numTrans;}
}