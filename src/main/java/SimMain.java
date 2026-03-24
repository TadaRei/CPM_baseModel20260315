import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SimMain {
	static final Object videoLock = new Object();
	
	public static void main(String[] args) {
		SimulationParams baseParams = new SimulationParams();
        // タイムスタンプ付きフォルダ名（YYYYMMDDhhmm形式）を生成
        String timestampedFolder = OutputManager.getTimestampedFolderName(baseParams.OUTPUT_FOLDER);
        OutputManager outputManager = new OutputManager(baseParams, timestampedFolder);
        outputManager.createOutputFolder();
       
        // 変更したいパラメータを表記
        ParameterSweep sweep = new ParameterSweep(baseParams)
                .varyLong("SEED", (p, v) -> p.RAND_SEED = v, 0, 1, 2, 3, 4);         // シード値5パターン
                //.varyInt("AREA", (p, v) -> p.TARGET_AREA = v, 100, 120, 140)        // 目標面積3パターン
                //.varyDouble("TEMP", (p, v) -> p.TEMPERATURE = v, 8.0, 10.0, 12.0);  // 温度3パターン
        
        List<SimulationParams> taskList = sweep.build();
        
        int cores = Runtime.getRuntime().availableProcessors();
        ExecutorService simExecutor = Executors.newFixedThreadPool(cores);
        
        for(SimulationParams p : taskList) {
        	simExecutor.submit(() -> {
        		try {
        			SimParrarel sim = new SimParrarel(p);
        			sim.run(timestampedFolder);
        		} catch (Exception e) {
        			e.printStackTrace();
        		}
        	});
        }
        simExecutor.shutdown();
	}
}
