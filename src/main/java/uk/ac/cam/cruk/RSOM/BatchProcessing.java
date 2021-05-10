package uk.ac.cam.cruk.RSOM;

import org.scijava.prefs.DefaultPrefService;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.io.FileSaver;
import ij.plugin.PlugIn;

public class BatchProcessing implements PlugIn {

	
	public RsomImageStack ris;
	public static String imgPath;
	protected static Boolean useCalibration = false;
	protected static double voxelSizeX = 20;
	protected static double voxelSizeY = 20;
	protected static double voxelSizeZ = 4;
	protected static String calUnit = "micron";
	public static Boolean getSlc = true;
	public static Boolean getRoi = true;
	public static Boolean getObj = true;
	public static int connIdx = 0;
	public static int connectivity = 26;
	public static Boolean getHst = true;
	public static double histMin = 20;
	public static double histMax = 260;
	public static int nBins = 10;
	
	public static void ProcessFolder (
			String root,
			String savePath
			) {


	}
	
	public static void listFolder() {
		
	}
	
	public static void listFile() {
		
	}
	
	public static void vesselAnalysis () {
		
	}
	
	public static void getResult() {
		
	}
	
	public static void calibrateImage() {
		
	}
	
	public static void createProjectionImages() {
		
	}
	
	public static void renameDataset() {
		
	}
	
	
	
	@Override
	public void run(String arg0) {
		
	}
	
	public static void main() {
		if (IJ.versionLessThan("1.52f")) System.exit(0);
		// make use of scijava parameter persistence storage	
		DefaultPrefService prefs = new DefaultPrefService();
		imgPath = prefs.get(String.class, "persistedString", imgPath);
		useCalibration = prefs.getBoolean(Boolean.class, "persistedBoolean", useCalibration);
		voxelSizeX = prefs.getDouble(Double.class, "persistedDouble", voxelSizeX);
		voxelSizeY = prefs.getDouble(Double.class, "persistedDouble", voxelSizeY);
		voxelSizeZ = prefs.getDouble(Double.class, "persistedDouble", voxelSizeZ);
		calUnit = prefs.get(String.class, "persistedString", calUnit);
		getSlc = prefs.getBoolean(Boolean.class, "persistedBoolean", getSlc);
		getRoi = prefs.getBoolean(Boolean.class, "persistedBoolean", getRoi);
		getObj = prefs.getBoolean(Boolean.class, "persistedBoolean", getObj);
		connIdx = prefs.getInt(Integer.class, "persistedDouble", connIdx);
		getHst = prefs.getBoolean(Boolean.class, "persistedBoolean", getHst);
		histMin = prefs.getDouble(Double.class, "persistedDouble", histMin);
		histMax = prefs.getDouble(Double.class, "persistedDouble", histMax);
		nBins = prefs.getInt(Integer.class, "persistedDouble", nBins);
		String [] ij_args = 
			{ "-Dplugins.dir=C:/Fiji.app/plugins",
			"-Dmacros.dir=C:/Fiji.app/macros" };
		ImageJ.main(ij_args);
		CheckAndSaveResult cs = new CheckAndSaveResult();

		//run();
	}
	
}
