import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Date; // ※ 注意: java.sql.Dateではなくjava.util.Dateが推奨される場合もあります。
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

/**
 * OutputManagerクラスは、シミュレーションの結果（画像、動画、パラメータ情報）を出力するための機能を提供します。
 * ・出力フォルダの作成（タイムスタンプ付きのフォルダ名を使用）
 * ・画像出力（グリッドの状態を指定の倍率で描画）
 * ・動画作成（疑似実装および実際にffmpegを呼び出して動画生成）
 * ・パラメータ情報の保存およびコンソール出力
 */
class OutputManager {
	private SimulationParams params;
	
    private int fileCounter = 0;	// ファイル名に付与する通し番号（画像やパラメータファイルの名前生成に使用）
    private String outputFolderName;	// 出力先のフォルダ名。タイムスタンプ付きで生成される（例："output/202303171230"）
    private String csvFilePath; // CSVファイルのパス
    
    /**
     * コンストラクタ
     * @param outputFolderName 出力先フォルダ名
     */
    public OutputManager(SimulationParams p, String outputFolderName) {
    	params = p;
    	
        this.outputFolderName = outputFolderName;
        // CSVファイルのパスを決定
        this.csvFilePath = outputFolderName + File.separator + "cell_data.csv";
    }

    /**
     * CSVファイルを初期化し、ヘッダー行を書き込みます。
     * ファイルが既に存在する場合は何もしません。
     */
    public void initializeCSV(Grid grid) {
        File csvFile = new File(this.csvFilePath);
        if (!csvFile.exists()) {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(this.csvFilePath))) {
                // --- 新しいヘッダーの作成 ---
                StringBuilder header = new StringBuilder();
                header.append("Step,TotalEnergy,NumTrans,"); // 先頭にステップと総エネルギー

                // 各細胞のヘッダー項目を追加
                for(int cellId = 1; cellId < params.NUM_CELLS; cellId++){
                    //if (cellId == 0) continue; // 背景はスキップ
                    header.append("CellID_").append(cellId).append(",");
                    header.append("Area_").append(cellId).append(",");
                    header.append("Perimeter_").append(cellId).append(",");
                    header.append("ContactEnergy_").append(cellId).append(",");
                    header.append("CentroidX_").append(cellId).append(",");
                    header.append("CentroidY_").append(cellId).append(",");
                    header.append("MCS_").append(cellId).append(",");
                }
                
                // 最後のカンマを削除して改行を追加
                if (header.length() > 0) {
                    header.deleteCharAt(header.length() - 1);
                }
                header.append("\n");

                writer.write(header.toString());
                System.out.println("CSV file created with new layout: " + this.csvFilePath);

            } catch (IOException e) {
                System.err.println("Failed to create or write CSV header: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    /**
     * getTimestampedFolderName() メソッド
     * 基本フォルダ名を受け取り、現在の時刻を「YYYYMMDDhhmm」形式で付与したフォルダ名を生成します。
     * @param baseFolder 基本となるフォルダ名（例："output"）
     * @return タイムスタンプ付きのフォルダ名（例："output/202303171230"）
     */
    public static String getTimestampedFolderName(String baseFolder) {
        SimpleDateFormat sdf_1 = new SimpleDateFormat("yyyyMMdd");
        SimpleDateFormat sdf_2 = new SimpleDateFormat("HHmm");

        // 現在の時刻を取得して、指定形式にフォーマット
        String timestamp = sdf_1.format(new Date(System.currentTimeMillis())) + "/" + sdf_2.format(new Date(System.currentTimeMillis()));
        return baseFolder + "/" + timestamp;
    }
    
    /**
     * createOutputFolder() メソッド
     * インスタンス変数 outputFolderName に従い、出力先フォルダを作成します。
     * 既に存在する場合はその旨を出力します。
     */
    public void createOutputFolder() {
        File folder = new File(this.outputFolderName);
        if (!folder.exists()) {
            boolean created = folder.mkdirs();
            if (created) {
                System.out.println("Folder '" + this.outputFolderName + "' created.");
            } else {
                System.out.println("Failed to create folder '" + this.outputFolderName + "'.");
            }
        } else {
            System.out.println("Folder '" + this.outputFolderName + "' already exists.");
        }
    }
    
    /**
     * getNextOutputFileName() メソッド
     * 指定されたプレフィックスと拡張子を用いて、通し番号付きの出力ファイル名を生成します。
     * 例: "frame_00001.png"
     * @param prefix 出力ファイルの接頭辞
     * @param extension ファイルの拡張子
     * @return 生成されたファイル名
     */
    public String getNextOutputFileName(String prefix, String extension) {
        return prefix + String.format("%05d", fileCounter++) + "." + extension;
    }
    
    /**
     * saveImage() メソッド
     * Gridオブジェクトの状態を画像として保存します。
     * 各セルは Parameters.CELL_SIZE 倍の大きさで描画されます。
     * @param grid 描画対象のグリッド
     * @param fileName 出力先のファイル名
     */
    public void saveImage(Grid grid, String fileName) {
        int gridWidth = grid.getWidth();
        int gridHeight = grid.getHeight();
        int cellSize = params.CELL_SIZE;
        int imageWidth = gridWidth * cellSize;
        int imageHeight = gridHeight * cellSize;
        // 画像バッファを生成
        BufferedImage image = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        Site[][] cells = grid.getGrid();
        // グリッド上の各セルを、対応する色で描画
        for (int i = 0; i < gridWidth; i++) {
            for (int j = 0; j < gridHeight; j++) {
                int id = cells[j][i].getId();
                g2d.setColor(getColorForId(id));
                g2d.fillRect(i * cellSize, j * cellSize, cellSize, cellSize);
            }
        }
        g2d.dispose();
        try {
            // 画像ファイルとして保存
            File outputFile = new File(fileName);
            ImageIO.write(image, params.IMAGE_FILE_EXTENSION, outputFile);
            System.out.println("Image saved: " + fileName);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 0と1で構成される2次元配列を、白と黒の二値画像として保存します。
     *
     * @param binaryGrid 0（白）と1（黒）で構成される2次元整数配列。
     * @param fileName 保存する画像ファイル名。
     */
    public void saveBinaryImage(int[][] binaryGrid, String fileName) {
        if (binaryGrid == null || binaryGrid.length == 0 || binaryGrid[0].length == 0) {
            System.err.println("Error: The provided grid is null or empty.");
            return;
        }

        int gridWidth = binaryGrid.length;
        int gridHeight = binaryGrid[0].length;
        int cellSize = params.CELL_SIZE;
        int imageWidth = gridWidth * cellSize;
        int imageHeight = gridHeight * cellSize;

        // 画像バッファを生成
        BufferedImage image = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();

        // グリッドの各値を元に描画
        for (int i = 0; i < gridWidth; i++) {
            for (int j = 0; j < gridHeight; j++) {
                // 値に応じて色を設定 (0: 白, 1: 黒)
            	
            	if(binaryGrid[j][i] == 0) g2d.setColor(Color.WHITE);else g2d.setColor(Color.BLACK);
                g2d.fillRect(j * cellSize, i * cellSize, cellSize, cellSize);
            }
        }

        // グラフィックスコンテキストを破棄し、画像を保存
        g2d.dispose();
        try {
            File outputFile = new File(fileName);
            ImageIO.write(image, params.IMAGE_FILE_EXTENSION, outputFile);
            System.out.println("Binary image saved: " + fileName);
        } catch (IOException e) {
            System.err.println("Error saving binary image: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * getColorForId() メソッド
     * 細胞IDに応じた色を決定します。IDが0の場合は白、それ以外は算出されたRGB値を返します。
     * @param id 細胞ID
     * @return 対応するColorオブジェクト
     */
    private Color getColorForId(int id) {
        if (id == 0) {
            return Color.WHITE;
        } else {
            int r = (id * 50) % 256;
            int g = (id * 80) % 256;
            int b = (id * 110) % 256;
            return new Color(r, g, b);
        }
    }
    
 // OutputManager.java のクラス内に以下のメソッドを追加します。

    /**
     * 【新規】グリッドの境界座標情報（境界記憶）を画像として保存します。
     * 細胞の境界となっているピクセルを黒、それ以外を白で描画します。
     * @param grid 描画対象のグリッド
     * @param fileName 出力先のファイル名
     */
    public void savePerimeterImage(Grid grid, String fileName) {
        // 1. Gridから境界座標情報を2次元配列として取得
        int[][] perimeterGrid = grid.perimeterCoordsToInt();

        // 2. 取得した2次元配列を二値画像として保存
        // 既存のsaveBinaryImageメソッドをそのまま利用できます
        saveBinaryImage(perimeterGrid, fileName);
        System.out.println("Perimeter image saved: " + fileName);
    }

    /**
     * createVideoWithFFmpeg() メソッド
     * ffmpegコマンドを利用して、出力フォルダ内の連番画像から実際に動画ファイルを生成します。
     * ※ この処理を実行するには、ffmpegがシステムにインストールされ、PATHまたは絶対パスでアクセス可能である必要があります。
     * @param outputVideoFileName 生成する動画ファイルのパスとファイル名
     */
    public void createVideoWithFFmpeg(String outputVideoFileName) {
        // 入力画像の連番パターン（例: outputFolderName/frame_%05d.png）
        String inputPattern = this.outputFolderName + File.separator + params.IMAGE_FILE_PREFIX + "%05d." + params.IMAGE_FILE_EXTENSION;
        // ffmpegに渡すコマンドリストを作成
        List<String> command = new ArrayList<>();
        command.add("/usr/local/bin/ffmpeg"); // ffmpegの絶対パス（システム環境に合わせて変更してください）
        command.add("-y"); // 出力ファイルが既に存在しても上書き
        command.add("-framerate");
        command.add("10"); // 10fpsで動画を生成
        command.add("-i");
        command.add(inputPattern); // 入力画像のパターン
        command.add("-c:v");
        command.add("libx264"); // ビデオコーデックとしてlibx264を指定
        command.add("-pix_fmt");
        command.add("yuv420p"); // 色空間の指定
        command.add(outputVideoFileName); // 出力動画ファイル名

        // ProcessBuilderを使って外部コマンド（ffmpeg）を実行
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true); // 標準エラー出力を標準出力にマージ
        try {
            Process process = pb.start();
            // ffmpegの出力内容を読み取り、コンソールに出力する
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
            // プロセスの終了コードを取得
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                System.out.println("Video successfully created: " + outputVideoFileName);
            } else {
                System.out.println("Error in video creation. Exit code: " + exitCode);
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 現在のGrid情報をCSVファイルに追記します。
     * @param grid データソースとなるGridオブジェクト
     * @param step 現在のシミュレーションステップ
     */
    public void saveGridDataToCSV(Grid grid, int step) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(this.csvFilePath, true))) { // true: 追記モード
            
            // --- 1行分のデータを作成 ---
            StringBuilder line = new StringBuilder();
            line.append(step).append(",").append(grid.getEnergy()).append(",").append(grid.getNumTrans()).append(",");

            // 各細胞のプロパティを順番に追記
            for (int cellId=1; cellId < params.NUM_CELLS; cellId++) {
                int area = grid.getCellAreas()[cellId];
                int perimeter = grid.getCellPerimeters()[cellId];
                int contact = grid.getCellContactEnergy()[cellId];

                line.append(cellId).append(",");
                line.append(area).append(",");
                line.append(perimeter).append(",");
                line.append(contact).append(",");
            }

            // 最後のカンマを削除して改行を追加
            if (line.length() > 0) {
                line.deleteCharAt(line.length() - 1);
            }
            line.append("\n");

            writer.write(line.toString());

        } catch (IOException e) {
            System.err.println("Error writing to CSV file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * saveOutput() メソッド
     * 現在のグリッド状態とエネルギー値を表示し、グリッドの最終状態を画像として保存します。
     * @param grid シミュレーション結果のグリッド
     */
    public void saveOutput(Grid grid) {
        System.out.println("Saving final output...");
        System.out.println("Energy: " + grid.getEnergy());
        // 通し番号付きの画像ファイル名を生成して保存
        String imagePath = this.outputFolderName + File.separator + getNextOutputFileName(params.IMAGE_FILE_PREFIX, params.IMAGE_FILE_EXTENSION);
        saveImage(grid, imagePath);
    }

    /**
     * saveParameters() メソッド
     * Parametersクラスのパラメータ情報をファイルに保存します。
     */
    public void saveParameters() {
        // 通し番号付きのパラメータファイル名を生成
        String paramFileName = this.outputFolderName + File.separator + getNextOutputFileName("parameters_", "txt");
        String paramData = params.getParametersString();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(paramFileName))) {
            writer.write(paramData);
            System.out.println("Parameters saved: " + paramFileName);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * printParameters() メソッド
     * Parametersクラスのパラメータ情報をコンソールに出力します。
     */
    public void printParameters() {
        System.out.println("Parameters:\n" + params.getParametersString());
    }
    
    /**
     * deleteImageFiles() メソッド
     * 出力フォルダ内に存在する、画像ファイル（Parameters.IMAGE_FILE_EXTENSIONで指定された拡張子のファイル）をすべて削除します。
     * 動画生成後など、不要な画像ファイルをクリーンアップする際に利用できます。
     */
    public void deleteImageFiles() {
        File folder = new File(this.outputFolderName);
        File[] files = folder.listFiles((dir, name) -> name.endsWith(params.IMAGE_FILE_EXTENSION));
        if (files != null) {
        	System.out.println("Files are deleting...");
            for (File file : files) {
                if (!file.delete()) {
                    System.out.println("Failed to delete image file: " + file.getName());
                }
            }
            System.out.println("All Files deleted");
        }
    }
    public void saveExecutionTime(long startTime, long endTime) {
        long durationMillis = endTime - startTime;
        
        // ミリ秒を秒に変換（小数点第3位まで保持するためdoubleにする）
        double durationSeconds = durationMillis / 1000.0;

        StringBuilder sb = new StringBuilder();
        sb.append("--------------------------------------------------\n");
        sb.append("Simulation Execution Report\n");
        sb.append("--------------------------------------------------\n");
        // 秒のみ出力する形式に変更
        sb.append(String.format("Execution Time : %.3f sec\n", durationSeconds)); 
        sb.append("Start Time     : ").append(new java.util.Date(startTime)).append("\n");
        sb.append("End Time       : ").append(new java.util.Date(endTime)).append("\n");
        sb.append("--------------------------------------------------\n");

        String reportContent = sb.toString();

        // 1. コンソールに出力
        System.out.println(reportContent);

        // 2. ファイルに出力 (outputフォルダ内に execution_time.txt を作成)
        File timeFile = new File(this.outputFolderName, "execution_time.txt");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(timeFile))) {
            writer.write(reportContent);
            System.out.println("Execution time saved: " + timeFile.getPath());
        } catch (IOException e) {
            System.err.println("Failed to save execution time: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
