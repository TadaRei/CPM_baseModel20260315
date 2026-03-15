import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SimMain {
	static final Object videoLock = new Object();
	
	public static void main(String[] args) {
		SimulationParams params = new SimulationParams();
        // タイムスタンプ付きフォルダ名（YYYYMMDDhhmm形式）を生成
        String timestampedFolder = OutputManager.getTimestampedFolderName(params.OUTPUT_FOLDER);
        OutputManager outputManager = new OutputManager(params, timestampedFolder);
        outputManager.createOutputFolder();
        
        int cores = Runtime.getRuntime().availableProcessors();
        ExecutorService simExecutor = Executors.newFixedThreadPool(cores);
       
        int[] seedList = {0,1,2,3,4};
        //int[] seedList = {5,6,7,8,9};
        
        for(int s : seedList) {
        	simExecutor.submit(() -> {
        		try {
        			SimulationParams p = new SimulationParams(params);
        			p.RAND_SEED = s;
        			
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
