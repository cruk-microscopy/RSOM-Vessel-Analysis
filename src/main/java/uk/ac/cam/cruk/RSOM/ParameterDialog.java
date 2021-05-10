package uk.ac.cam.cruk.RSOM;

import fiji.util.gui.GenericDialogPlus;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.plugin.FolderOpener;
import ij.plugin.PlugIn;

import java.awt.Color;
//import java.awt.Color;
import java.awt.Font;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

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
	protected Boolean doCalibration = true;
	protected double voxelSizeX = 20;
	protected static double voxelSizeY = 20;
	protected static double voxelSizeZ = 4;
	protected static String calUnit = "micron";
	protected static boolean isImageSequence = false;
	protected static boolean doSubtractBackground = true;
	protected static double bgRadius = 5;
	
	protected String[] methods = new String[] {"Default","Huang","Intermodes","IsoData",
			"IJ_IsoData","Li","MaxEntropy","Mean","MinError","Minimum","Moments",
			"Otsu","Percentile","RenyiEntropy","Shanbhag","Triangle","Yen"};
	protected static int autoOrManualThreshold = 0;
	protected static boolean doManualThreshold = false;
	protected static String autoMethod = "Moments";
	
	protected static boolean doMedianFilter = true;
	protected static double mdFtRadius = 2.5;
	
	protected static int roiOrWholeImage = 0;
	protected static boolean doRoi = true;
	protected static boolean loadRoi = false;
	protected static String selectionPath;
	
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
	
	// parameter for batch processing
	protected static boolean doNoSampleCode = false;
	protected static double bgRadiusMin = 5;
	protected static double bgRadiusStep = 5;
	protected static double bgRadiusMax = 5;
	protected static String csvMethodString = "mom,otsu";
	protected static double mdRadiusMin = 2.5;
	protected static double mdRadiusStep = 1;
	protected static double mdRadiusMax = 2.5;
	
	protected ArrayList<Double> bgRadiuses;
	protected ArrayList<String> autoMethods;
	protected ArrayList<Double> mdRadiuses;
	protected ArrayList<String> methodNames;
	protected ArrayList<methodSet> methodSets;
	
	protected class methodSet {
		public double bgRadius;
		public String method;
		public double mdRadius;
		public String methodAlias;
		// default constructor
		public methodSet(double bgRadius, String method, double mdRadius, String methodAlias) {
			this.bgRadius = bgRadius;
			this.method = method;
			this.mdRadius = mdRadius;
			this.methodAlias = methodAlias;
		}
		
		@Override
	    public int hashCode() {
	        return methodAlias.hashCode();
	    }
	    @Override
	    public boolean equals(Object obj) {
	        if (!(obj instanceof methodSet))
	            return false;
	        methodSet m = (methodSet) obj;
	        return m.methodAlias.equals(methodAlias);
	    }
	}
	
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
	
	public void main() {
		
		if (IJ.versionLessThan("1.52f")) System.exit(0);
		
		// make use of scijava parameter persistence storage	
		DefaultPrefService prefs = new DefaultPrefService();
		filePath = prefs.get(String.class, "RSOM-filePath", filePath);
		doCalibration = prefs.getBoolean(Boolean.class, "RSOM-doCalibration", doCalibration);
		voxelSizeX = prefs.getDouble(Double.class, "RSOM-voxelSizeX", voxelSizeX);
		voxelSizeY = prefs.getDouble(Double.class, "RSOM-voxelSizeY", voxelSizeY);
		voxelSizeZ = prefs.getDouble(Double.class, "RSOM-voxelSizeZ", voxelSizeZ);
		calUnit = prefs.get(String.class, "RSOM-calUnit", calUnit);
		
		doSubtractBackground = prefs.getBoolean(Boolean.class, "RSOM-doSubtractBackground", doSubtractBackground);
		bgRadius = prefs.getDouble(Double.class, "RSOM-bgRadius", bgRadius);
		
		autoOrManualThreshold = prefs.getInt(Integer.class, "RSOM-autoOrManualThreshold", autoOrManualThreshold);
		autoMethod = prefs.get(String.class, "RSOM-autoMethod", autoMethod);
		doMedianFilter = prefs.getBoolean(Boolean.class, "RSOM-doMedianFilter", doMedianFilter);
		mdFtRadius = prefs.getDouble(Double.class, "RSOM-mdFtRadius", mdFtRadius);
		
		roiOrWholeImage = prefs.getInt(Integer.class, "RSOM-mdFtRadius", roiOrWholeImage);
		loadRoi = prefs.getBoolean(Boolean.class, "RSOM-loadRoi", loadRoi);
		selectionPath = prefs.get(String.class, "RSOM-selectionPath", selectionPath);
		doSmooth = prefs.getBoolean(Boolean.class, "RSOM-doSmooth", doSmooth);
		smoothLv = prefs.getDouble(Double.class, "RSOM-smoothLv", smoothLv);
		downSizeLv = prefs.getDouble(Double.class, "RSOM-downSizeLv", downSizeLv);
		shrinkSize = prefs.getDouble(Double.class, "RSOM-shrinkSize", shrinkSize);
		
		connIdx = prefs.getInt(Integer.class, "RSOM-connIdx", connIdx);
		nBins = prefs.getInt(Integer.class, "RSOM-nBins", nBins);
		saveMask = prefs.getBoolean(Boolean.class, "RSOM-saveMask", saveMask);
		saveROI = prefs.getBoolean(Boolean.class, "RSOM-saveROI", saveROI);
		saveObjMap = prefs.getBoolean(Boolean.class, "RSOM-saveObjMap", saveObjMap);
		saveDiameterMap = prefs.getBoolean(Boolean.class, "RSOM-saveDiameterMap", saveDiameterMap);
		//prefs.put(String.class, "persistedString", filePath);
		//addDialog();
		ParameterDialog pd = new ParameterDialog();
		pd.run(null);
		
		prefs.put(String.class, "RSOM-filePath", filePath);
		prefs.put(Boolean.class, "RSOM-doCalibration", doCalibration);
		prefs.put(Double.class, "RSOM-voxelSizeX", voxelSizeX);
		prefs.put(Double.class, "RSOM-voxelSizeY", voxelSizeY);
		prefs.put(Double.class, "RSOM-voxelSizeZ", voxelSizeZ);
		prefs.put(String.class, "RSOM-calUnit", calUnit);
		
		prefs.put(Boolean.class, "RSOM-doSubtractBackground", doSubtractBackground);
		prefs.put(Double.class, "RSOM-bgRadius", bgRadius);
		
		prefs.put(Integer.class, "RSOM-autoOrManualThreshold", autoOrManualThreshold);
		prefs.put(String.class, "RSOM-autoMethod", autoMethod);
		prefs.put(Boolean.class, "RSOM-doMedianFilter", doMedianFilter);
		prefs.put(Double.class, "RSOM-mdFtRadius", mdFtRadius);
		
		prefs.put(Integer.class, "RSOM-roiOrWholeImage", roiOrWholeImage);
		prefs.put(Boolean.class, "RSOM-loadRoi", loadRoi);
		prefs.put(String.class, "RSOM-selectionPath", selectionPath);
		prefs.put(Boolean.class, "RSOM-doSmooth", doSmooth);
		prefs.put(Double.class, "RSOM-smoothLv", smoothLv);
		prefs.put(Double.class, "RSOM-downSizeLv", downSizeLv);
		prefs.put(Double.class, "RSOM-shrinkSize", shrinkSize);
		
		prefs.put(Integer.class, "RSOM-connIdx", connIdx);
		prefs.put(Integer.class, "RSOM-nBins", nBins);
		prefs.put(Boolean.class, "RSOM-saveMask", saveMask);
		prefs.put(Boolean.class, "RSOM-saveROI", saveROI);
		prefs.put(Boolean.class, "RSOM-saveObjMap", saveObjMap);
		prefs.put(Boolean.class, "RSOM-saveDiameterMap", saveDiameterMap);
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
		gd.addDirectoryOrFileField("_selection3D.zip:", selectionPath);
		gd.setInsets(0,159,0);
		gd.addCheckbox("Smooth 3D selection", doSmooth);
		gd.addSlider("Smoothing Level(%):", 0, 100, smoothLv, 5);
		gd.addSlider("Down-sizing Level (%):", 0, 80, downSizeLv, 1);
		gd.addSlider("Shrink-Enlarge ROI (µm)", -5000, 5000, shrinkSize, 20);
		//gd.addNumericField("Shrink ROI by:", shrinkSize, 1, 4, "micron");
		
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
				 +" version: 1.6.2<br>"
				 +" date: 2019.04.09<br>"
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
		selectionPath = gd.getNextString();
		
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
	
	public Boolean addDialogBatch() {
		
		final Font highlightFont = new Font("Helvetica", Font.BOLD, 12);
		final Color highlightColor = Color.BLUE;
		
		GenericDialogPlus gd = new GenericDialogPlus("RSOM vessel analysis - generate mask with different parameter sets");
		
		// File open options
		String processMessage = "Pre-processing options:";
		gd.setInsets(10,0,0);
		gd.addMessage(processMessage, highlightFont);
		gd.setInsets(0,149,0);
		gd.addCheckbox("Process data with unrecognized sample code", doNoSampleCode);
		gd.setInsets(0,149,0);
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
		String parameterMessage = "Parameter Sets:";
		gd.setInsets(10,0,0);
		gd.addMessage(parameterMessage, highlightFont, highlightColor);
		gd.setInsets(0,149,0);
		gd.addCheckbox("Subtract background", doSubtractBackground);
		gd.addNumericField("radius min:", bgRadiusMin, 1, 3, "pixel");
		gd.addNumericField("radius step:", bgRadiusStep, 1, 3, "pixel");
		gd.addNumericField("radius max:", bgRadiusMax, 1, 3, "pixel");

		//gd.setInsets(0,0,0);
		//gd.addChoice("method:", methods, autoMethod);
		gd.addStringField("Threshold methods:",  csvMethodString, 15);
		gd.setInsets(0,149,0);
		gd.addCheckbox("Smooth mask (median filter)", doMedianFilter);
		gd.addNumericField("radius min:", mdRadiusMin, 1, 3, "pixel");
		gd.addNumericField("radius step:", mdRadiusStep, 1, 3, "pixel");
		gd.addNumericField("radius max:", mdRadiusMax, 1, 3, "pixel");
		
		//	3D selection option group
		String RoiMessage = "Selection (ROI) options:";
		gd.setInsets(10,0,0);
		gd.addMessage(RoiMessage, highlightFont);
		String[] roiOption = new String[] {"Selected areas", "Whole image"};
		gd.setInsets(0,0,0);
		gd.addChoice("ROI:", roiOption, roiOption[roiOrWholeImage]);
		gd.setInsets(0,149,0);
		gd.addCheckbox("Load 3D selection file from result folder", loadRoi);
		gd.setInsets(0,149,0);
		gd.addCheckbox("Smooth 3D selection", doSmooth);
		gd.addSlider("Smoothing Level(%):", 0, 100, smoothLv, 5);
		gd.addSlider("Down-sizing Level (%):", 0, 80, downSizeLv, 1);
		gd.addSlider("Shrink-Enlarge ROI (µm)", -2000, 2000, shrinkSize, 20);
		
		//	help html text
		String html = "<html>"
				 +"<h2>RSOM blood vessel analysis (ImageJ plugin)</h2>"
				 +"    Batch processing with different parameters<br>"
				 +" version: 1.6.2<br>"
				 +" date: 2019.04.09<br>"
				 +" author: Ziqiang Huang (Ziqiang.Huang@cruk.cam.ac.uk)<br><br>"
				 +"<h3>Usage:</h3>"
				 +"<&nbsp>Choose a root folder containing RSOM image tif stacks.<br>"
				 +" <&nbsp>Unique sample code will be recognized as e.g.: R_154837_20180312<br>"
				 + "which is \"R_\" followed by time code, underscore, and date code.<br>"
				 +"<br><&nbsp>Different combination of parameters (parameter sets)<br>"
				 +"will be taken to create binary mask(s) of the stack.<br>"
				 +" <&nbsp>For background subtraction and median filtering<br>"
				 +" , user need to specify the minimum and maximum radius<br>"
				 +" , and step size between them.<br>"
				 +"<&nbsp>For thresholding methods<br>"
				 +" , user need to type in keywords of the method choosen<br>"
				 +" , and separate different method keywords by comma.<br>"
				 +"<br><&nbsp>Be aware if the keywords is not specific enough<br>"
				 +" , all methods containing that keywords will be applied.<br>"
				 +"<br><&nbsp>Be careful with the range and number of parameters<br>"
				 +" , as the final number of results will be a permutation of all parameters given.<br>"
				 +"    i.e.: 2 background subtration radius, 2 thresholding method<br>"
				 +" , and 2 median filter raidus will result in 8 masks.<br>"
				 +"<br><&nbsp>The generated mask image will be saved to result folder.<br>"
				 +" , with parameter-set as identifier in the end of the mask file name.<br>";
		gd.addHelp(html);
		gd.showDialog();
		
		doNoSampleCode = gd.getNextBoolean();
		doCalibration = gd.getNextBoolean();
		voxelSizeX = gd.getNextNumber();
		voxelSizeY = gd.getNextNumber();
		voxelSizeZ = gd.getNextNumber();
		calUnit = gd.getNextString();
		
		doSubtractBackground = gd.getNextBoolean();
		bgRadiusMin = gd.getNextNumber();
		bgRadiusStep = gd.getNextNumber();
		bgRadiusMax = gd.getNextNumber();
		
		csvMethodString = gd.getNextString();
		
		doMedianFilter = gd.getNextBoolean();
		mdRadiusMin = gd.getNextNumber();
		mdRadiusStep = gd.getNextNumber();
		mdRadiusMax = gd.getNextNumber();
		
		roiOrWholeImage = gd.getNextChoiceIndex();
		if (roiOrWholeImage == 0) doRoi = true;
		else	doRoi = false;
		
		doSmooth = gd.getNextBoolean();
		smoothLv = gd.getNextNumber();
		downSizeLv = gd.getNextNumber();
		shrinkSize = gd.getNextNumber();
		
		if (gd.wasCanceled())	return false;
		
		
		
		//HashMap<Double, String, Double, String> methodSets = new HashMap<Double, String, Double, String>();
		bgRadiuses = parseDoubles(bgRadiusMin, bgRadiusMax, bgRadiusStep);
		autoMethods = parseThresholdMethods(csvMethodString);
		mdRadiuses = parseDoubles(mdRadiusMin, mdRadiusMax, mdRadiusStep);
		methodNames = getMethodsNames (doSubtractBackground, bgRadiuses, autoMethods, doMedianFilter, mdRadiuses);
		
		methodSets = getMethodSets (doSubtractBackground, bgRadiuses, autoMethods, doMedianFilter, mdRadiuses);
		//methodNames = getMethodsNames (methodSets);
		
		return true;
	}
	
	public ArrayList<Double> parseDoubles (
			double min,
			double max,
			double step
			) {
		ArrayList<Double> values = new ArrayList<Double>();
		double value = min;
		while (value<max) {
			values.add(value);
			value += step;
		}
		values.add(max);
		return values;
	}
	
	public ArrayList<String> parseStrings (
			String csvString
			) {
		return new ArrayList<String>(Arrays.asList(csvString.split(",")));
	}
	
	public ArrayList<String> parseThresholdMethods (
			String methodStrings
			) {
		ArrayList<String> methodParts = parseStrings(methodStrings);
		ArrayList<String> method = new ArrayList<String>();

		for (int i=0; i<methods.length; i++) {
			for (String parts : methodParts) {
				if (methods[i].toLowerCase().contains(parts.toLowerCase())) {
					method.add(methods[i]);
					break;
				}
			}
		}
		return method;
	}
	
	public ArrayList<String> getMethodsNames (
			Boolean doBackground,
			ArrayList<Double> bgRadiuses,
			ArrayList<String> autoMethods,
			Boolean doMedianFilter,
			ArrayList<Double> mdRadiuses
			) {
		
		ArrayList<String> methodNames = new ArrayList<String>();
		String bgString = null;
		String thString = null;
		String mdString = null;
		
		for (double bgRadius : bgRadiuses) {
			if (!doBackground) bgString = "sub0,0";
			else	bgString = "sub" + String.valueOf(bgRadius).replace(".",",");
			for (String autoMethod : autoMethods) {
				thString = autoMethod.substring(0,3).toUpperCase();
				for (double mdRadius : mdRadiuses) {
					if (!doMedianFilter) mdString = "med0,0";
					else	mdString = "med" + String.valueOf(mdRadius).replace(".",",");
					methodNames.add(bgString + thString + mdString);
				}
			}
		}
		
		LinkedHashSet<String> hashSet = new LinkedHashSet<>(methodNames);
        ArrayList<String> methodNamesWithoutDuplicates = new ArrayList<>(hashSet);
		return methodNamesWithoutDuplicates;
	}
	
	public ArrayList<String> getMethodsNames (
			ArrayList<methodSet> methodSets
			) {
		
		ArrayList<String> methodNames = new ArrayList<String>();
		int size = methodSets.size();
		for (int i=0; i<size; i++) {
			methodNames.add(methodSets.get(i).methodAlias);
		}
		return methodNames;
	}
	
	public ArrayList<methodSet> getMethodSets (
			Boolean doBackground,
			ArrayList<Double> bgRadiuses,
			ArrayList<String> autoMethods,
			Boolean doMedianFilter,
			ArrayList<Double> mdRadiuses
			) {
		ArrayList<methodSet> methodSets = new ArrayList<methodSet>();
		
		String bgString = null;
		String thString = null;
		String mdString = null;
		
		for (double bgRadius : bgRadiuses) {
			if (!doBackground) {
				bgRadius = 0;
				bgString = "sub0,0";
			} else	{
				bgString = "sub" + String.valueOf(bgRadius).replace(".",",");
			}
			for (String autoMethod : autoMethods) {
				thString = autoMethod.substring(0,3).toUpperCase();
				for (double mdRadius : mdRadiuses) {
					if (!doMedianFilter) {
						mdRadius = 0;
						mdString = "med0,0";
					} else	{
						mdString = "med" + String.valueOf(mdRadius).replace(".",",");
					}
					String methodAlias = bgString + thString + mdString;
					methodSet newMethod = new methodSet(bgRadius, autoMethod, mdRadius, methodAlias);
					methodSets.add(newMethod);
				}
			}
		}
		
		//Set<methodSet> uniqueElements = new HashSet<methodSet>(methodSets);
		//methodSets.clear();
		//methodSets.addAll(uniqueElements);
		return methodSets;
	}
	
}