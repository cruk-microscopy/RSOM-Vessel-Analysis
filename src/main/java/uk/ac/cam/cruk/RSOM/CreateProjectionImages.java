package uk.ac.cam.cruk.RSOM;

//import java.awt.BasicStroke;
//import java.awt.Color;
import java.awt.Font;
import java.io.File;

import org.scijava.prefs.DefaultPrefService;

import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.ImageCanvas;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.gui.Toolbar;
import ij.measure.Calibration;
import ij.plugin.Duplicator;
import ij.plugin.FolderOpener;
//import ij.plugin.LutLoader;
import ij.plugin.PlugIn;
import ij.plugin.RoiInterpolator;
import ij.plugin.ZProjector;
import ij.plugin.frame.RoiManager;
import ij.process.FloatPolygon;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;



public class CreateProjectionImages implements PlugIn {

	protected int activeImgCount = WindowManager.getImageCount();
	protected int[] wList = WindowManager.getIDList();
	protected static int activeImgNum = -1;
	protected boolean getActiveImage = false;
	protected Boolean isImageSequence;
	protected static String filePath;

	protected static Boolean doRoi = false;
	protected static Boolean getActiveRoi = false;
	protected static String roiPath;
	protected RoiManager rmSelection;
	
	protected static Boolean xProject = false;
	protected static Boolean yProject = false;
	protected static Boolean zProject = true;
	protected static Boolean maxProject = false;
	protected static Boolean meanProject = false;
	protected static Boolean volProject = true;
	protected static Boolean sumProject = false;
	protected static Boolean stdevProject = false;
	protected static Boolean minProject = false;
	protected static Boolean medianProject = false;
	
	protected static Boolean useCalibration = true;
	protected static double xCal = 20;
	protected static double yCal = 20;
	protected static double zCal = 4;
	protected static String unitCal = "micron";
	
	protected static Boolean doInterpolate = false;
	protected static double interpolateSpace = 4.000;
	
	protected static Boolean useLut = false;
	
	protected static String[] LUTs = {"Grays", "Fire", "Red Hot", "royal"
			, "unionjack", "thal", "glasbey on dark", "edges", "brgbcmyw"
			, "16 colors", "6 shades", "Red", "Green", "Blue"};
	protected static int lutStrIdx = 1;
	protected static String lutStr;
	
	public String[] activeImageList() {
		if (wList == null) return null;
		String[] titles = new String[wList.length+1];
		for (int i=0; i<wList.length; i++) {
			ImagePlus imp = WindowManager.getImage(wList[i]);
			titles[i] = imp!=null?imp.getTitle():"";	
		}
		titles[wList.length] = "open image on disk";
		return titles;
	}
	
	public ImagePlus addDialog() {
		final Font highlightFont = new Font("Helvetica", Font.BOLD, 12);
		
		GenericDialogPlus gd = new GenericDialogPlus("Create Projection Images");

		// image open option
		String foString = "Input Image Options:";
		gd.setInsets(10,0,0);
		gd.addMessage(foString, highlightFont);
		
		if (activeImgCount!=0) {
			String [] imgTitles = activeImageList();
			gd.setInsets(0,0,0);
			gd.addChoice("Get active image", imgTitles, imgTitles[activeImgCount]);
		}
		gd.addDirectoryOrFileField("path", filePath);
		gd.setInsets(0,168,0);
		gd.addCheckbox("Only operate within ROI", doRoi);
		gd.setInsets(0,168,0);
		gd.addCheckbox("Use current ROI Manager", getActiveRoi);
		gd.addFileField("Load 3D selection from disk", roiPath);
		// projection axis option group
		String axString = "Projection Axis:";
		gd.setInsets(10,0,0);
		gd.addMessage(axString, highlightFont);
		int axRow = 1, axColumn = 3;
		String[] axLabels = {"X - (YZ)","Y - (XZ)","Z - (XY)"};
		boolean[] axStates = {xProject,yProject,zProject};
		gd.setInsets(0,168,0);
		gd.addCheckboxGroup(axRow, axColumn, axLabels, axStates);
		
		// projection method option group
		String mdString = "Projection Method:";
		gd.setInsets(10,0,0);
		gd.addMessage(mdString, highlightFont);
		gd.setInsets(0,168,0);
		gd.addCheckbox("3D", volProject);
		int mdRow = 2, mdColumn = 3;
		String[] mdLabels = {"Max", "Mean", "Sum"
				, "Standard Deviation", "Min", "Median"};
		boolean[] mdStates = {maxProject, meanProject
				, sumProject, stdevProject, minProject, medianProject};
		gd.setInsets(0,168,0);
		gd.addCheckboxGroup(mdRow, mdColumn, mdLabels, mdStates);
		
		// projection visualisation option group
		String visString = "Projection Visualization:";
		gd.setInsets(10,0,0);
		gd.addMessage(visString, highlightFont);
		gd.setInsets(0,168,0);
		gd.addCheckbox("Use input image calibration", useCalibration);
		gd.setInsets(0,0,3);
		gd.addNumericField("X",xCal,0,2,"");
		gd.setInsets(2,0,3);
		gd.addNumericField("Y",yCal,0,2,"");
		gd.setInsets(2,0,3);
		gd.addNumericField("Z",zCal,0,2,"");
		gd.setInsets(2,0,3);
		gd.addStringField("unit",unitCal);
		
		gd.setInsets(0,168,0);
		gd.addCheckbox("Do pixel interpolation", doInterpolate);
		gd.addToSameRow();
		gd.addNumericField("spacing",interpolateSpace,3,4,"micron");

		gd.setInsets(0,168,0);
		gd.addCheckbox("Use input image LUT", useLut);
		//gd.setInsets(0,0,0);
		gd.addToSameRow();
		gd.addChoice("LUT list:", LUTs, LUTs[lutStrIdx]);
		
		gd.showDialog();
		
		if (activeImgCount!=0) {
			activeImgNum = gd.getNextChoiceIndex();
			if (activeImgNum!=activeImgCount) {
				getActiveImage = true;
			}
		}
		
		filePath = gd.getNextString();

		doRoi = gd.getNextBoolean();
		getActiveRoi = gd.getNextBoolean();
		roiPath = gd.getNextString();
		
		xProject = gd.getNextBoolean();
		yProject = gd.getNextBoolean();
		zProject = gd.getNextBoolean();
		
		volProject = gd.getNextBoolean();
		maxProject = gd.getNextBoolean();
		meanProject = gd.getNextBoolean();
		sumProject = gd.getNextBoolean();
		stdevProject = gd.getNextBoolean();
		minProject = gd.getNextBoolean();
		medianProject = gd.getNextBoolean();
		
		useCalibration = gd.getNextBoolean();
		xCal = gd.getNextNumber();
		yCal = gd.getNextNumber();
		zCal = gd.getNextNumber();
		unitCal = gd.getNextString();
		
		doInterpolate = gd.getNextBoolean();
		interpolateSpace = gd.getNextNumber();
		
		useLut = gd.getNextBoolean();
		lutStrIdx = gd.getNextChoiceIndex();
		lutStr = LUTs[lutStrIdx];

		if (gd.wasCanceled()) return null;
		
		final ImagePlus imp;
		File imgF = new File(filePath);
		if (imgF.isDirectory())	isImageSequence = true;
		else	isImageSequence = false;
		if (getActiveImage) {
			imp = WindowManager.getImage(wList[activeImgNum]);
		} else {
			if (isImageSequence) {
				imp = FolderOpener.open(filePath,"file=tif");
			} else {
				imp = IJ.openImage(filePath);
			}
			if (imp==null) {
				IJ.log("Error loading image and/or ROI.");
				return null;
			}
		}
		imp.show();
		return imp;
	}
	
	// known issue, after execution RoiManager ROis disappear
	public ImagePlus getRoi (
			ImagePlus imp,
			Boolean getCurrentRoiManager,
			String path
			) {

		RoiManager rm = RoiManager.getInstance2();
		//RoiManager rm = RoiManager.getRoiManager();
		if (rm == null) {
			if (getCurrentRoiManager) {
				IJ.log("RoiManager is empty, use whole image instead.");
				imp.deleteRoi();
				return imp;
			} else {
				rm = new RoiManager();
				rm.runCommand("Open", path);
				rm.setVisible(false);
			}
		} else {
			if (!getCurrentRoiManager) {
				rm.reset();
				rm.runCommand("Open", path);
				rm.setVisible(false);
			}
		}
		rm.runCommand(imp, "Sort");
		//Color sColor = rm.getRoi(rm.getCount()-2).getStrokeColor();
		
		int x = imp.getWidth();
		int y = imp.getHeight();
		int z = imp.getNSlices();
		
		int c = imp.getNChannels();
		int t = imp.getNFrames();
		
		int nROI = rm.getCount();
		if (nROI < 2) {
			rm.close();
			IJ.log("Not enough entry in RoiManager"
					+ ", use whole image instead.");
			imp.deleteRoi();
			return imp;
		}
		
		for (int i=0; i<nROI; i++) {
			rm.select(imp, i);
			Roi rmi = rm.getRoi(i);
			if (rmi.getType() == Roi.LINE || 
					rmi.getType() == Roi.POINT || 
					rmi.getType() == Roi.FREELINE) {
				rm.runCommand("Delete");
			}
			/*
			if (rmi.getType() == Roi.POLYGON) {
				FloatPolygon poly = rmi.getInterpolatedPolygon(1.0, true);
				int type = rmi.isLine()?Roi.FREELINE:Roi.FREEROI;
				ImageCanvas ic = imp.getCanvas();
				if (poly.npoints<=150 && ic!=null && ic.getMagnification()>=12.0)
					type = rmi.isLine()?Roi.POLYLINE:Roi.POLYGON;
				Roi p = new PolygonRoi(poly,type);
				if (rmi.getStroke()!=null)
					p.setStrokeWidth(rmi.getStrokeWidth());
				p.setStrokeColor(rmi.getStrokeColor());
				p.setName(rmi.getName());
				p.setDrawOffset(rmi.getDrawOffset());
				rm.setRoi(p, i);
			}
			*/
		}
		
		/*
		RoiInterpolator ri = new RoiInterpolator();
		ri.run("");
		*/
		rm.runCommand(imp,"Interpolate ROIs");
		nROI = rm.getCount();
		
		rm.select(imp,0);
		int minRoiSlice = (imp.getSlice());
		rm.select(imp,nROI-1);
		int maxRoiSlice = (imp.getSlice());
		for (int i=1; i<z+1; i++) {
			if (i<minRoiSlice || i>maxRoiSlice) {
				imp.setZ(i);
				for (int j=1; j<c+1; j++) {
					imp.setC(j);
					for (int k=1; k<t+1; k++) {
						imp.setT(k);
						ImageProcessor ip = imp.getProcessor();
						ip.setColor(Toolbar.getBackgroundColor());
						ip.fillRect(0, 0, x, y);
					}
				}
			}
		}
		for (int i=0; i<nROI; i++) {
			rm.select(imp, i);
			IJ.run(imp, "Clear Outside", "slice");
			/* stroke color and fill color error with RoiManager
			Roi rmi = rm.getRoi(i);
			rmi.setStrokeColor(sColor);
			rmi.setFillColor(Color.BLACK);
			imp.getProcessor().fillOutside(rmi);
			*/
		}
		rm.runCommand("Update");
		return imp;
	}
	
	public ImagePlus[] doAxisProjection (
			ImagePlus imp,
			Boolean useImageLut,
			String lut,
			Boolean doX,
			Boolean doY,
			Boolean doZ
			) {
		ImagePlus impX =  null;
		ImagePlus impY =  null;
		ImagePlus impZ =  null;
		String paramStr = "output=" + String.valueOf(interpolateSpace) + " start=";
		if (doX) {
			//Duplicator dp = new Duplicator();
			//impX = dp.run(imp);
			paramStr += "Left";
			if (!doInterpolate) paramStr += " avoid";
			IJ.run(imp, "Reslice [/]...", paramStr);
			impX = WindowManager.getImage("Reslice of "+imp.getShortTitle());
			impX.setTitle(imp.getShortTitle()+"_X-Project");
			if (useImageLut) {
				impX.setLut(imp.getProcessor().getLut());
			} else {
				IJ.run(impX, lut, "");
			}
			impX.hide();
		}
		paramStr = "output=" + String.valueOf(interpolateSpace) + " start=";
		if (doY) {
			//Duplicator dp = new Duplicator();
			//impY = dp.run(imp);
			paramStr += "Top";
			if (!doInterpolate) paramStr += " avoid";
			IJ.run(imp, "Reslice [/]...", paramStr);
			impY = WindowManager.getImage("Reslice of "+imp.getShortTitle());
			impY.setTitle(imp.getShortTitle()+"_Y-Project");
			if (useImageLut) {
				impY.setLut(imp.getProcessor().getLut());
			} else {
				IJ.run(impY, lut, "");
			}
			impY.hide();
		}
		if (doZ) {
			Duplicator dp = new Duplicator();
			impZ = dp.run(imp);
			impZ.setTitle(imp.getShortTitle()+"_Z-Project");
			if (useImageLut) {
				impZ.setLut(imp.getProcessor().getLut());
			} else {
				IJ.run(impZ, lut, "");
			}
			impZ.hide();
		}
		ImagePlus[] XYZ = {impX, impY, impZ};
		return XYZ;
	}
	
	public void doZProjection (
			ImagePlus inputImp,
			Boolean useImageLut,
			String lut,
			Boolean[] inputMethods) {
		String[] methodsStr = {"max", "avg", "sum", "sd", "min", "median"};
		ImagePlus[] all = {null, null, null, null, null, null};
		if (inputImp == null) return;
		for (int i=0; i<inputMethods.length; i++) {
			if (inputMethods[i]) {
				all[i] = ZProjector.run(inputImp, methodsStr[i]);
				if (useImageLut) {
					all[i].setLut(inputImp.getProcessor().getLut());
				} else {
					IJ.run(all[i], lut, "");
				}
				all[i].show();
			}
		}
		return;
	}
	
	
	@Override
	public void run(String arg0) {
		
		ImagePlus inputImage = addDialog();
		if (inputImage == null) return;
		if (inputImage.getBitDepth() == 24) {
			IJ.log("Input is RGB image:");
			IJ.log("LUT will not be applicable.");
			IJ.log("Created projection images will be RGB format as well.");
			useLut = false;
			lutStr = "Revert";	//nasty way of ignoring RGB LUT
		} else if (inputImage.getBitDepth() == 8) {
			ImageStatistics stats = inputImage.getStatistics();
			if (stats.histogram[0]+stats.histogram[255]==stats.pixelCount) {
				// binary mask LUT issue:
				// 3D, Max, Min, Median projection images will also be binary.
				IJ.log("Input is binary mask:");
				IJ.log("Some LUT will not show full color range.");
			}
		}
		
		// use 3D selection ROI to crop the input image
		// pad with NaN or 0 background
		if (doRoi) {
			inputImage = getRoi(inputImage, getActiveRoi, roiPath);
		}
		inputImage.updateImage();
		inputImage.deleteRoi();
		
		// calibrate input image
		Calibration origCal = inputImage.getCalibration();
		if (!useCalibration) {
			inputImage.getCalibration().pixelWidth = xCal;
			inputImage.getCalibration().pixelHeight = yCal;
			inputImage.getCalibration().pixelDepth = zCal;
			inputImage.getCalibration().setUnit(unitCal);
		}

		// do axis projection
		ImagePlus[] XYZ = doAxisProjection(inputImage
				, useLut, lutStr, xProject, yProject, zProject);
		
		inputImage.setCalibration(origCal);
		//inputImage.revert();
		
		// do projection method
		Boolean[] methods = {maxProject, meanProject, sumProject
				, stdevProject, minProject, medianProject};
		for (int i=0; i<XYZ.length; i++) {
			if (XYZ[i] == null)
				continue;
			if (volProject) {
				int midSlice = XYZ[i].getNSlices()/2;
				XYZ[i].show();
				XYZ[i].setZ(midSlice);
			}
			doZProjection(XYZ[i], useLut, lutStr, methods);
		}
	}
	
	public static void main(String[] args) {
		
		if (IJ.versionLessThan("1.52f")) System.exit(0);
		
		DefaultPrefService dps = new DefaultPrefService();
		
		filePath = dps.get(String.class, "persistedString", filePath);
		doRoi = dps.getBoolean(Boolean.class, "persistedBoolean", doRoi);
		getActiveRoi = dps.getBoolean(Boolean.class, "persistedBoolean", getActiveRoi);
		roiPath = dps.get(String.class, "persistedString", roiPath);
		
		xProject = dps.getBoolean(Boolean.class, "persistedBoolean", xProject);
		yProject = dps.getBoolean(Boolean.class, "persistedBoolean", yProject);
		zProject = dps.getBoolean(Boolean.class, "persistedBoolean", zProject);
		maxProject = dps.getBoolean(Boolean.class, "persistedBoolean", maxProject);
		meanProject = dps.getBoolean(Boolean.class, "persistedBoolean", meanProject);
		volProject = dps.getBoolean(Boolean.class, "persistedBoolean", volProject);
		sumProject = dps.getBoolean(Boolean.class, "persistedBoolean", sumProject);
		stdevProject = dps.getBoolean(Boolean.class, "persistedBoolean", stdevProject);
		minProject = dps.getBoolean(Boolean.class, "persistedBoolean", minProject);
		medianProject = dps.getBoolean(Boolean.class, "persistedBoolean", medianProject);
		
		useCalibration = dps.getBoolean(Boolean.class, "persistedBoolean", useCalibration);
		
		xCal = dps.getDouble(Double.class, "persistedDouble", xCal);
		yCal = dps.getDouble(Double.class, "persistedDouble", yCal);
		zCal = dps.getDouble(Double.class, "persistedDouble", zCal);
		unitCal = dps.get(String.class, "persistedString", unitCal);
		
		doInterpolate = dps.getBoolean(Boolean.class, "persistedBoolean", doInterpolate);
		interpolateSpace = dps.getDouble(Double.class, "persistedDouble", interpolateSpace);
		
		useLut = dps.getBoolean(Boolean.class, "persistedBoolean", useLut);
		lutStrIdx = dps.getInt(Integer.class, "persistedDouble", lutStrIdx);

		String [] ij_args = 
			{ "-Dplugins.dir=C:/Fiji.app/plugins",
			"-Dmacros.dir=C:/Fiji.app/macros" };

		ImageJ.main(ij_args);
		CreateProjectionImages np = new CreateProjectionImages();
		np.run(null);
	}
	
}
