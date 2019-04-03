package uk.ac.cam.cruk.RSOM;

import fiji.util.gui.GenericDialogPlus;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.plugin.FolderOpener;
import ij.plugin.PlugIn;

//import java.awt.Color;
import java.awt.Font;
import java.io.File;

import org.scijava.prefs.DefaultPrefService;
//import org.scijava.prefs.PrefService;

// !!! persistence in testing mode, not fully configured
// Known problem: after restart Fiji, previous parameters are lost
public class ParameterDialog implements PlugIn {
	
	// input parameters with initialised default value
	//protected static int activeImgCount = WindowManager.getImageCount();
	//protected static int activeImgCount;
	
	protected static boolean getActiveImage = false;
	protected static int activeImgNum = -1;
	protected static String filePath;
	protected static Boolean doCalibration = true;
	protected static double voxelSizeX = 20;
	protected static double voxelSizeY = 20;
	protected static double voxelSizeZ = 4;
	protected static String calUnit = "micron";
	protected static boolean isImageSequence = false;
	protected static boolean doSubtractBackground = true;
	protected static double bgRadius = 5;
	
	protected static int autoOrManualThreshold = 0;
	protected static boolean doManualThreshold = false;
	protected static String autoMethod = "Moments";
	
	protected static boolean doMedianFilter = true;
	protected static double mdFtRadius = 2.5;
	
	protected static int roiOrWholeImage = 0;
	protected static boolean doRoi = true;
	protected static boolean loadRoi = false;
	protected static String roiPath;
	
	protected static boolean doSmooth = true;
	protected static double smoothLv = 15;
	protected static double downSizeLv = 50;
	protected static double shrinkSize = 0;
	
	protected static int connIdx = 0;
	protected static int connectivity = 26;
	protected static int nBins = 10;
	protected static boolean saveMask = true;
	protected static boolean saveROI = true;
	protected static boolean saveObjMap = true;
	protected static boolean saveDiameterMap = true;
	
	public String[] activeImageList() {
		/*
		int activeImgCount = WindowManager.getImageCount();
		if (activeImgCount == 0) {
			String[] titles = new String[1];
			titles[0] = "open image on disk";
			return titles;
		} else {
			int[] wList = WindowManager.getIDList();
			String[] titles = new String[wList.length+1];
			titles[0] = "open image on disk";
			for (int i=0; i<wList.length; i++) {
				ImagePlus imp = WindowManager.getImage(wList[i]);
				titles[i+1] = imp!=null?imp.getTitle():"";	
			}
			return titles;
		}	
		*/
		int numOpenImg = WindowManager.getImageCount();
		String[] titles = new String[numOpenImg+1];
		titles[0] = "open image on disk";
		System.arraycopy(WindowManager.getImageTitles(), 0, titles, 1, numOpenImg);
		return titles;
	}
	
	@Override
	public void run(String arg0) {
		addDialog();
		ImagePlus img = null;
		if (getActiveImage) {
			int activeImageID = WindowManager.getNthImageID(activeImgNum);
			img = WindowManager.getImage(activeImageID);
		} else {
			if (isImageSequence) {
				img = FolderOpener.open(filePath,"file=tif");
			} else {
				img = IJ.openImage(filePath);
			}
		}
		if (img == null) {
			IJ.log("Input RSOM image wrong!");
			return;
		}
	}
	
	public static void main() {
		
		if (IJ.versionLessThan("1.52f")) System.exit(0);
		
		// make use of scijava parameter persistence storage	
		DefaultPrefService prefs = new DefaultPrefService();
		filePath = prefs.get(String.class, "persistedString", filePath);
		doCalibration = prefs.getBoolean(Boolean.class, "persistedBoolean", doCalibration);
		voxelSizeX = prefs.getDouble(Double.class, "persistedDouble", voxelSizeX);
		voxelSizeY = prefs.getDouble(Double.class, "persistedDouble", voxelSizeY);
		voxelSizeZ = prefs.getDouble(Double.class, "persistedDouble", voxelSizeZ);
		calUnit = prefs.get(String.class, "persistedString", calUnit);
		
		doSubtractBackground = prefs.getBoolean(Boolean.class, "persistedBoolean", doSubtractBackground);
		bgRadius = prefs.getDouble(Double.class, "persistedDouble", bgRadius);
		
		autoOrManualThreshold = prefs.getInt(Integer.class, "persistedDouble", autoOrManualThreshold);
		autoMethod = prefs.get(String.class, "persistedString", autoMethod);
		doMedianFilter = prefs.getBoolean(Boolean.class, "persistedBoolean", doMedianFilter);
		mdFtRadius = prefs.getDouble(Double.class, "persistedDouble", mdFtRadius);
		
		roiOrWholeImage = prefs.getInt(Integer.class, "persistedDouble", autoOrManualThreshold);
		loadRoi = prefs.getBoolean(Boolean.class, "persistedBoolean", loadRoi);
		roiPath = prefs.get(String.class, "persistedString", roiPath);
		doSmooth = prefs.getBoolean(Boolean.class, "persistedBoolean", doSmooth);
		smoothLv = prefs.getDouble(Double.class, "persistedDouble", smoothLv);
		downSizeLv = prefs.getDouble(Double.class, "persistedDouble", downSizeLv);
		shrinkSize = prefs.getDouble(Double.class, "persistedDouble", shrinkSize);
		
		connIdx = prefs.getInt(Integer.class, "persistedDouble", connIdx);
		nBins = prefs.getInt(Integer.class, "persistedDouble", nBins);
		saveMask = prefs.getBoolean(Boolean.class, "persistedBoolean", saveMask);
		saveROI = prefs.getBoolean(Boolean.class, "persistedBoolean", saveROI);
		saveObjMap = prefs.getBoolean(Boolean.class, "persistedBoolean", saveObjMap);
		saveDiameterMap = prefs.getBoolean(Boolean.class, "persistedBoolean", saveDiameterMap);
		//prefs.put(String.class, "persistedString", filePath);
		//addDialog();
		ParameterDialog pd = new ParameterDialog();
		pd.run(null);
	}
	
	public Boolean addDialog() {
		
		final Font highlightFont = new Font("Helvetica", Font.BOLD, 12);
		//final Color highlightColor = Color.BLUE;
		
		GenericDialogPlus gd = new GenericDialogPlus("RSOM vessel analysis");
		//	file open option group
		String fileOpenMessage = "File open options:";
		gd.setInsets(10,0,0);
		gd.addMessage(fileOpenMessage, highlightFont);
		String [] imgTitles = activeImageList();
		gd.setInsets(0,0,0);
		gd.addChoice("Get active image", imgTitles, imgTitles[0]);
		gd.addDirectoryOrFileField("Open image on disk", filePath);
		gd.setInsets(-6,159,0);
		gd.addCheckbox("Calibrate image", doCalibration);
		gd.setInsets(0,0,0);
		gd.addNumericField("X:", voxelSizeX, 1);
		gd.setInsets(0,0,0);
		gd.addNumericField("Y:", voxelSizeY, 1);
		gd.setInsets(0,0,0);
		gd.addNumericField("Z:", voxelSizeZ, 1);
		gd.setInsets(0,0,0);
		gd.addStringField("Unit:", calUnit);
		//	pre-processing option group
		String processMessage = "Pre-processing options:";
		gd.setInsets(10,0,0);
		gd.addMessage(processMessage, highlightFont);
		gd.setInsets(0,159,0);
		gd.addCheckbox("Subtract background", doSubtractBackground);
		gd.addToSameRow();
		gd.addNumericField("radius:", bgRadius, 1, 3, "pixel");
		String[] autoOrManual = new String[] {"Auto", "Manual"};
		String[] methods = new String[] {"Default","Huang","Intermodes","IsoData",
			"IJ_IsoData","Li","MaxEntropy","Mean","MinError","Minimum","Moments",
			"Otsu","Percentile","RenyiEntropy","Shanbhag","Triangle","Yen"};
		gd.setInsets(0,0,0);
		gd.addChoice("Threshoding:", autoOrManual, autoOrManual[autoOrManualThreshold]);
		gd.addToSameRow();
		gd.addChoice("method:", methods, autoMethod);
		gd.setInsets(0,159,0);
		gd.addCheckbox("Smooth mask (median filter)", doMedianFilter);
		gd.addToSameRow();
		gd.addNumericField("radius:", mdFtRadius, 1, 3, "pixel");
		//	3D selection option group
		String RoiMessage = "Selection (ROI) options:";
		gd.setInsets(10,0,0);
		gd.addMessage(RoiMessage, highlightFont);
		String[] roiOption = new String[] {"Selected areas", "Whole image"};
		gd.setInsets(0,0,0);
		gd.addChoice("ROI:", roiOption, roiOption[roiOrWholeImage]);
		gd.setInsets(0,159,0);
		gd.addCheckbox("Load 3D selection file from disk", loadRoi);
		gd.addDirectoryOrFileField("_selection3D.zip:", roiPath);
		gd.setInsets(0,159,0);
		gd.addCheckbox("Smooth 3D selection", doSmooth);
		gd.addSlider("Smoothing Level(%):", 0, 100, smoothLv);
		gd.addSlider("Down-sizing Level (%):", 0, 80, downSizeLv);
		gd.addNumericField("Shrink ROI by:", shrinkSize, 1, 4, "micron");
		
		// result saving option group
		String saveMessage = "Result saving options:";
		gd.setInsets(10,0,0);
		gd.addMessage(saveMessage, highlightFont);
		
		String[] connChoice = new String[] {"26", "6"};
		gd.addChoice("3D ojbect connectivity:", connChoice, connChoice[connIdx]);
		gd.addNumericField("histogram number of bins:", nBins, 0);
		int row = 2, column = 2;
		String[] labels = {"binary mask","2D ROIs","object map","diameter map"};
		boolean[] states = {saveMask,saveROI,saveObjMap,saveDiameterMap};
		gd.setInsets(0,159,0);
		gd.addCheckboxGroup(row, column, labels, states);
		String html = "<html>"
				 +"<h2>RSOM blood vessel analysis (ImageJ plugin)</h2>"
				 +" version: 1.5.4<br>"
				 +" date: 2019.03.26<br>"
				 +" author: Ziqiang Huang (Ziqiang.Huang@cruk.cam.ac.uk)<br><br>"
				 +"<h3>Usage:</h3>"
				 +"<&nbsp>Choose RSOM image stack from disk or from active<br>"
				 +" image lists in Fiji.<br>"
				 +" <&nbsp>Both tiff stack and image sequence are accepted. Image<br>"
				 +" sequence is recongnized automatically.<br>"
				 +"<br><&nbsp>Thresholding to get a binary mask of the stack.<br>"
				 +" <&nbsp>If choose manual thresholding, a dialog box going to<br>"
				 +" pop up and ask for user to select the thresholding bounds.<br>"
				 +" <&nbsp>Always use the stack histogram to calculate threshold.<br>"
				 +"<br><&nbsp>User can define a 3D selection (ROI), or load a pre-<br>"
				 +" existing ROI file from disk.<br>"
				 +" <&nbsp>If the ROI file was created by previous plugin execution,<br>"
				 +" then the file name should be image file name followed by<br>"
				 +" \"_selection3D.zip\", and it should be a ROIset file that can<br>"
				 +" be loaded by ROI Manager in Fiji.<br>"
				 +"<br><&nbsp>Computation results, intermediate image stacks, as well<br>"
				 +" as an execution log will be saved based on user choices.<br>"
				 +" <&nbsp>The results will be stored to a folder nested next to the<br>"
				 +" input image file.<br>"
				 +" <&nbsp>Be aware if certain results are not ticked, the execution<br>"
				 +" might be skipped.<br>"
				 +"<br><&nbsp>Known issue: diameter map only take isotropic voxel as input.<br>"
				 +"<br><&nbsp>Known issue: diameter map does not return system memory (4 times the input file).<br>";
		gd.addHelp(html);
		gd.showDialog();
		
		activeImgNum = gd.getNextChoiceIndex();
		if (activeImgNum!=0)	getActiveImage = true;
		else	getActiveImage = false;
		filePath = gd.getNextString();
		doCalibration = gd.getNextBoolean();
		voxelSizeX = gd.getNextNumber();
		voxelSizeY = gd.getNextNumber();
		voxelSizeZ = gd.getNextNumber();
		calUnit = gd.getNextString();
		
		doSubtractBackground = gd.getNextBoolean();
		bgRadius = gd.getNextNumber();
		autoOrManualThreshold = gd.getNextChoiceIndex();
		if (autoOrManualThreshold == 1) doManualThreshold = true;
		else	doManualThreshold = false;
		autoMethod = gd.getNextChoice();
		doMedianFilter = gd.getNextBoolean();
		mdFtRadius = gd.getNextNumber();
		
		roiOrWholeImage = gd.getNextChoiceIndex();
		if (roiOrWholeImage == 0) doRoi = true;
		else	doRoi = false;
		loadRoi = gd.getNextBoolean();
		roiPath = gd.getNextString();
		
		doSmooth = gd.getNextBoolean();
		smoothLv = gd.getNextNumber();
		downSizeLv = gd.getNextNumber();
		shrinkSize = gd.getNextNumber();
		
		connIdx = gd.getNextChoiceIndex();
		connectivity = Integer.parseInt(connChoice[connIdx]);
		nBins = (int)gd.getNextNumber();
		saveMask = gd.getNextBoolean();
		saveROI = gd.getNextBoolean();
		saveObjMap = gd.getNextBoolean();
		saveDiameterMap = gd.getNextBoolean();
		
		if (gd.wasCanceled())	return false;
		
		File imgF = new File(filePath);
		if (imgF.isDirectory())	isImageSequence = true;
		else	isImageSequence = false;
		
		return true;
	}
}