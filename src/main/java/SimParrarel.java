import java.io.File;

public class SimParrarel {
	private SimulationParams params;
	
	private static final boolean USE_GPU = true;
	private static final boolean MAKE_MOVIE = false;
	
	public SimParrarel(SimulationParams p) {
		params = p;
	}
	
	public void run(String timestampedFolder) {
		// ★計測開始時刻を取得
        long startTime = System.currentTimeMillis();
		
		String folderName = "random_seed_" + (params.RAND_SEED);
		folderName = timestampedFolder + "/" + folderName;
		OutputManager outputManager = new OutputManager(params, folderName);
		outputManager.createOutputFolder();

        // パラメータ情報を保存・出力
        outputManager.saveParameters();
        outputManager.printParameters();

        // シミュレーションの初期化
        ISimulator simulator;
        
        if(USE_GPU) {
        	System.out.println("--- Running on GPU Mode ---");
        	simulator = new SimulatorGPUParrarel(params, params.GRID_WIDTH, params.GRID_HEIGHT);
        } else {
        	System.out.println("--- Running on Non Parrarel CPU Mode ---");
        	simulator = new Simulator(params, params.GRID_WIDTH, params.GRID_HEIGHT);
        }
        
        outputManager.initializeCSV(simulator.getGrid());

        double steps = 0.0;
        int intervalSteps = 0;
        
		while (steps < params.SIMULATION_MCS) {
			simulator.runMonteCarloStep();
			steps = simulator.getNumSteps();
			if (intervalSteps <= (int) steps) {
				simulator.ticks();
				if(MAKE_MOVIE && (intervalSteps+1)%/*128*/1000 == 0) {
					System.out.println("MCS: " + (String.format("%.3f", steps)) + " / " + params.SIMULATION_MCS);
					String imagePath = folderName + File.separator + outputManager
							.getNextOutputFileName(params.IMAGE_FILE_PREFIX, params.IMAGE_FILE_EXTENSION);
					outputManager.saveImage(simulator.getGrid(), imagePath);
				}
				outputManager.saveGridDataToCSV(simulator.getGrid(), intervalSteps);
				intervalSteps++;
			}
		}
		long endTime = System.currentTimeMillis();
		outputManager.saveExecutionTime(startTime, endTime);
		
        // 新規機能：ffmpegを利用して実際に動画を生成する
		if(MAKE_MOVIE) {
			String videoFileName = folderName + File.separator + "simulation_video.mp4";
	        outputManager.createVideoWithFFmpeg(videoFileName);
	        //outputManager.deleteImageFiles();
		}
		outputManager.saveImage(simulator.getGrid(), folderName + File.separator + outputManager.getNextOutputFileName(params.IMAGE_FILE_PREFIX, params.IMAGE_FILE_EXTENSION));
        String perimeterImagePath = folderName + File.separator + outputManager.getNextOutputFileName(params.IMAGE_FILE_PREFIX, params.IMAGE_FILE_EXTENSION);
        outputManager.savePerimeterImage(simulator.getGrid(), perimeterImagePath);
	}
}
