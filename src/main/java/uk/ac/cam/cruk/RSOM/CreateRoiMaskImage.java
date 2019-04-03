package uk.ac.cam.cruk.RSOM;

import java.awt.Color;
import java.awt.Point;
import java.io.File;

import org.scijava.prefs.DefaultPrefService;
import org.scijava.vecmath.Color3f;

import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.Undo;
import ij.WindowManager;
import ij.gui.ImageCanvas;
import ij.gui.NewImage;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.gui.Toolbar;
import ij.plugin.Duplicator;
import ij.plugin.FolderOpener;
import ij.plugin.PlugIn;
import ij.plugin.RGBStackMerge;
import ij.plugin.frame.RoiManager;
import ij.process.FloatPolygon;
import ij.process.LUT;
import ij.process.StackConverter;
import ij.process.StackStatistics;
import ij3d.Image3DUniverse;


public class CreateRoiMaskImage implements PlugIn {
	
	protected static String filePath;
	protected Boolean isImageSequence;
	protected static String roiPath;
	protected static Boolean fillRoi = true;
	protected static float transparency;
	protected RoiManager rmSelection;
	protected static Boolean getActiveRoi = false;
	protected static int visOption = 2;
	protected static String[] Colors = {"RED", "YELLOW", "GREEN", "BLUE",
	"MAGENTA", "PINK", "CYAN", "ORANGE", "WHITE", "BLACK", "GRAY"};
	protected static int colorIndexVessel = 0;
	protected static int colorIndexSelection = 1;
	protected static Color colorVessel;
	protected static Color colorSelection;
	protected static Boolean do3D = true;
	protected static Boolean doCalibration = true;
	protected int activeImgCount = WindowManager.getImageCount();
	protected int[] wList = WindowManager.getIDList();
	protected String[] imgTitles;
	protected boolean getActiveImage = false;
	protected int activeImgNum = -1;
	
	public ImagePlus addDialog() {
		GenericDialogPlus gd = new GenericDialogPlus("3D selection check");
		if (activeImgCount!=0) {
			String [] imgTitles = activeImageList();
			gd.addChoice("Get active image", imgTitles, imgTitles[activeImgCount]);
		}
		gd.addDirectoryOrFileField("Open image on disk", filePath);
		gd.setInsets(0,168,0);
		gd.addCheckbox("Use image calibration", doCalibration);
		gd.setInsets(20,168,0);
		gd.addCheckbox("Use current ROI Manager", getActiveRoi);
		gd.addFileField("Load 3D selection from disk", roiPath);
		gd.setInsets(0,168,0);
		gd.addCheckbox("Fill top and bottom ROI", fillRoi);
		gd.addToSameRow();
		gd.addNumericField("transparency:", transparency, 1, 3, "%");
		String[] visOptions = {"RGB merged","selection as Overlay"
				, "multi-channel composite"};
		gd.setInsets(20,0,5);
		gd.addChoice("Visualization option", visOptions, visOptions[visOption]);
		gd.addChoice("Color of vessel image:", Colors, Colors[colorIndexVessel]);
		gd.addChoice("Color of selection:", Colors, Colors[colorIndexSelection]);
		gd.setInsets(0,168,0);
		gd.addCheckbox("Create 3D rendering", do3D);
		gd.showDialog();
		
		if (activeImgCount!=0) {
			activeImgNum = gd.getNextChoiceIndex();
			if (activeImgNum!=activeImgCount) {
				getActiveImage = true;
			}
		}
		filePath = gd.getNextString();
		doCalibration = gd.getNextBoolean();
		getActiveRoi = gd.getNextBoolean();
		roiPath = gd.getNextString();
		fillRoi = gd.getNextBoolean();
		transparency = (float)gd.getNextNumber();
		visOption = gd.getNextChoiceIndex();
		colorIndexVessel = gd.getNextChoiceIndex();
		colorIndexSelection = gd.getNextChoiceIndex();
		do3D = gd.getNextBoolean();
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
				IJ.log("Input mask image wrong!");
				return null;
			}
		}
		colorVessel = getColorFromString(Colors[colorIndexVessel]);
		colorSelection = getColorFromString(Colors[colorIndexSelection]);
		
		if (fillRoi) {
			transparency = transparency<0?0:transparency;
			transparency = transparency>100?100:transparency;
		}
		return imp;
	}


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
	
	// function to recognise mask
	// not used for the moment
	public int getMaskImageNum() {
		int maskImgNum = 0;
		String [] imgTitles = activeImageList();
		if (imgTitles != null) {
			for (int i=0; i<imgTitles.length; i++) {
				if (imgTitles[i].contains("mask")) {
					maskImgNum++;
					activeImgNum = i;
				}
			}
		}
		return maskImgNum;
	}
	
	public static Roi interpolate(
			Roi roi,
			double interval,
			Boolean smooth) {
		//imp = WindowManager.getCurrentImage();
		//roi = imp.getRoi();
		//Undo.setup(Undo.ROI, imp);
		FloatPolygon poly = roi.getInterpolatedPolygon(interval, smooth);
		int type = Roi.FREEROI;
		//ImageCanvas ic = imp.getCanvas();
		Roi p = new PolygonRoi(poly,type);
		if (roi.getStroke()!=null)
			p.setStrokeWidth(roi.getStrokeWidth());
		p.setStrokeColor(roi.getStrokeColor());
		p.setName(roi.getName());
		if (roi.getStroke()!=null)
			p.setStroke(roi.getStroke());
		p.setDrawOffset(roi.getDrawOffset());
		return p;
		//imp.setRoi(p);
	}
	public static Color getColorFromString (String col) {
		Color color = Color.WHITE;
		switch (col) {
	    	case "RED":
	    		color = Color.RED;
	    		break;
	    	case "YELLOW":
		        color = Color.YELLOW;
		        break;
	    	case "GREEN":
		        color = Color.GREEN;
		        break;
	    	case "BLUE":
		        color = Color.BLUE;
		        break;
	    	case "MAGENTA":
		        color = Color.MAGENTA;
		        break;
	    	case "PINK":
		        color = Color.PINK;
		        break;
	    	case "CYAN":
		        color = Color.CYAN;
		        break;
	    	case "ORANGE":
		        color = Color.ORANGE;
		        break;
	    	case "WHITE":
		        color = Color.WHITE;
		        break;
		    case "BLACK":
		        color = Color.BLACK;
		        break;
		    case "GRAY":
		        color = Color.GRAY;
		        break;
	        }
	    return color;
	}
	
	
	public static RoiManager modify3DSelection (
			ImagePlus imp,
			RoiManager inputRoi,
			Boolean fill,
			Float alpha
			) {
		
		inputRoi.runCommand("Sort");
		int nROI = inputRoi.getCount();
		
		for (int i=0; i<nROI; i++) {
			Roi r = inputRoi.getRoi(i);
			if (r.getType() == Roi.LINE || 
					r.getType() == Roi.POINT || 
					r.getType() == Roi.FREELINE) {
				inputRoi.runCommand("Delete");
			}
			if (r.getType() == Roi.POLYGON) {
				/*	low level, incomplete implementation
				FloatPolygon poly = r.getInterpolatedPolygon(0.5, true);
				Roi p = new PolygonRoi(poly,Roi.POLYGON);
				if (r.getStroke()!=null)
					p.setStrokeWidth(r.getStrokeWidth());
				p.setStrokeColor(r.getStrokeColor());
				p.setName(r.getName());
				inputRoi.setRoi(p, i);
				*/
				inputRoi.select(imp, i);
				r = interpolate(r, 1.0, true);
				//IJ.run(imp, "Interpolate", "interval=1 smooth");
				inputRoi.runCommand(imp, "Update");
			}
		}
		
		if (!RoiManagerUtility.isInterpolatable()) {
			IJ.error("current ROI Manager is not suitable for 3D selection check.");
			return null;
		}
		
		inputRoi.runCommand(imp, "Sort");
		inputRoi.runCommand(imp, "Interpolate ROIs");
		inputRoi.runCommand(imp, "Sort");
		
		if (fill) {
			Color fillColor = new Color(
					colorSelection.getColorSpace(),
					colorSelection.getRGBColorComponents(null),
					alpha); // alpha level 1.0 = opaque
			Roi rTop = inputRoi.getRoi(0);
			rTop.setFillColor(fillColor);
			inputRoi.setRoi(rTop, 0);
			Roi rBottom = inputRoi.getRoi(inputRoi.getCount()-1);
			rBottom.setFillColor(fillColor);
			inputRoi.setRoi(rBottom, inputRoi.getCount()-1);
			inputRoi.runCommand(imp, "Sort");
		}
		
		return inputRoi;
	}
	
	public ImagePlus doRGBMerge(
			ImagePlus vessel,
			Boolean calibrate,
			RoiManager rm,
			Boolean fill,
			Float trans,
			Color colorVessel,
			Color colorSelection,
			int visOption
			) {
		Float alpha = 1-trans/100;
		rm = modify3DSelection(vessel, rm, fill, alpha);
		int nROI = rm.getCount();
		// if (!getActiveRoi) rm.setVisible(false);
		vessel.deleteRoi();
		Duplicator dp = new Duplicator();
		ImagePlus vesselChannel = dp.run(vessel);
		//vesselChannel.getWindow().setVisible(false);
		if (!calibrate) {
			vesselChannel.getCalibration().pixelWidth = 1;
			vesselChannel.getCalibration().pixelHeight = 1;
			vesselChannel.getCalibration().pixelDepth = 1;
			vesselChannel.getCalibration().setUnit("pixel");
		}
		if (vessel.getBitDepth()==32) {
			IJ.log("Input image stack is 32 bit.");
			IJ.log("Will only display the brightest 1% voxel in stack.");
			StackStatistics stats = new StackStatistics(vesselChannel);
			double signalFraction = stats.pixelCount*0.01;
			long[] histogram = stats.getHistogram();
			int sum = 0; int binMin = 0; int binMax = 0;
			for (int i=255; i>0; i--) {
				binMin = i; sum += histogram[i];
				if (sum > signalFraction) break;
			}
			binMax = binMin<249?binMin+7:256;
			double hMin = stats.binSize * binMin;
			double hMax = stats.binSize * binMax;
			vesselChannel.setDisplayRange(hMin,	hMax);
			//ContrastEnhancer ce = new ContrastEnhancer();
			//ce.stretchHistogram(vesselChannel,0.01);
			//ce.equalize(vesselChannel);
		}
		StackConverter sc = new StackConverter(vesselChannel);
		sc.convertToGray8();
		LUT lutVessel = LUT.createLutFromColor(colorVessel);
		vesselChannel.setLut(lutVessel);
		int [] dim = vesselChannel.getDimensions();
		Color roiDefaultColor = Roi.getColor();
		switch (visOption) {
		case 0: //RGB
			/*	Use RoiManager to create RGB image
			 *  will not work with 3D convex hull;
			 *  Require further inspection:
			 *  Known problem:
			 *  	RoiManager.draw() only draw outline without fill;
			 *  	imp.flattenStack() has bug with RGB stack;
			 *  	imp.flatten() need updateImage on every slice; 
			Color defaultForegroundColor = Toolbar.getForegroundColor();
			Toolbar.setForegroundColor(colorSelection);
			sc = new StackConverter(vesselChannel);
			sc.convertToRGB();
			ImagePlus imp2;
			int s = 0;
			for (int i=0; i<nROI; i++) {
				IJ.log("debug, rm i:" + String.valueOf(i));
				vesselChannel.killRoi();
				rm.select(vesselChannel, i);
				vesselChannel.updateAndDraw();
				s = vesselChannel.getZ();
				IJ.log("debug, vessel channel z:" + String.valueOf(s));
				imp2 = vesselChannel.flatten();
				vesselChannel.getStack().setProcessor(imp2.getProcessor(),s);
				imp2.flush();	
			}
			Toolbar.setForegroundColor(defaultForegroundColor);
			vesselChannel.setTitle("_RGB"); 
			return vesselChannel;
			*/
			
			/* Make use of overlay as intermediate stack to
			 * create RGB stack. Use imp.flatten() to generate
			 * RGB slice-wise and append to new RGB stack
			 */
			Roi.setColor(colorSelection);
			//vesselChannel.hide();
			rm.moveRoisToOverlay(vesselChannel);
			vesselChannel.getOverlay().setStrokeColor(colorSelection);
			Roi.setColor(roiDefaultColor);
			sc.convertToRGB();
			
			//vesselChannel.show();
			int z = vesselChannel.getNSlices();
			ImagePlus rgb = NewImage.createRGBImage(
					"_RGB", dim[0], dim[1], dim[3], NewImage.FILL_BLACK);
			rgb.hide();
			for (int i=1; i<z+1; i++) {
				vesselChannel.setZ(i);
				vesselChannel.updateImage();
				rgb.setZ(i);
				rgb.setProcessor(vesselChannel.flatten().getProcessor());
			}
			rgb.setCalibration(vesselChannel.getCalibration());
			vesselChannel.close();
			vesselChannel.flush();
			rgb.show();
			return rgb;
		case 1:	//selection as overlay
			Roi.setColor(colorSelection);
			rm.moveRoisToOverlay(vesselChannel);
			vesselChannel.getOverlay().setStrokeColor(colorSelection);
			Roi.setColor(roiDefaultColor);
			vesselChannel.setTitle("_Overlay");
			//vesselChannel.getWindow().setVisible(true);
			return vesselChannel;
		case 2: //composite
			ImagePlus selectionChannel = NewImage.createByteImage(
					"3D selection mask", dim[0], dim[1], dim[3], NewImage.FILL_BLACK);
			if (calibrate) {
				selectionChannel.setCalibration(vessel.getCalibration());
			}
			Toolbar.setForegroundColor(Color.WHITE);
			for (int i=0; i<nROI; i++) {
				rm.select(selectionChannel, i);
				if (i == 0 || i == nROI-1) {
					if (fill) {
						int fillValue = (int)(255 * alpha);
						selectionChannel.getProcessor().setColor(fillValue);
						selectionChannel.getProcessor().fill(rm.getRoi(i));
					}
				}
				rm.runCommand(selectionChannel,"Draw");
			}
			LUT lutSelection = LUT.createLutFromColor(colorSelection);
			selectionChannel.setLut(lutSelection);
			vesselChannel.setLut(lutVessel);
			ImagePlus multiChannelComposite = RGBStackMerge.mergeChannels(
					new ImagePlus[]{vesselChannel, selectionChannel}, false);
			if (calibrate) {
				multiChannelComposite.setCalibration(vessel.getCalibration());
			}
			vesselChannel.close(); //vesselChannel.flush();
			selectionChannel.close(); //selectionChannel.flush();
			multiChannelComposite.setTitle("_Composite");
			return multiChannelComposite;
		}
		return null;
	}
	
	
	public void do3DRendering(
			ImagePlus imp,
			int resamplingFactor
			) {
			Color3f cf = new Color3f(300,300,300);
			boolean[] channels = new boolean[] {true,true,true};
			Image3DUniverse univ = new Image3DUniverse();
			univ.show();
			univ.addVoltex(imp,cf,imp.getTitle()+" 3D rendering",1
					,channels,resamplingFactor);
			}
	
	@Override
	public void run(String arg0) {
		
		// get input image (vessel image or vessel mask image)
		ImagePlus maskImg = addDialog();
		if (maskImg == null) return;
		
		
		// prepare RoiManager for operation
		rmSelection = RoiManager.getInstance2();
		Roi[] oriRois = null; Boolean rmHidden = false; Point rmLocation = null;
		if (RoiManagerUtility.isOpen() && !RoiManagerUtility.isEmpty()) {
			oriRois = RoiManagerUtility.managerToRoiArray();
			rmHidden = RoiManagerUtility.isHidden();
			rmLocation = RoiManagerUtility.getLocation();
		}
		RoiManagerUtility.hideManager();
		if (!getActiveRoi) {
			RoiManagerUtility.resetManager();
			rmSelection = RoiManager.getInstance2();
			IJ.redirectErrorMessages();
			rmSelection.runCommand("Open",roiPath);
		}
		if (!RoiManagerUtility.isInterpolatable()) {
			IJ.error("ROI Manager is not suitable for 3D selection check.");
			RoiManagerUtility.roiArrayToManager(oriRois, true, false);
			RoiManagerUtility.setLocation(rmLocation);
			if (!rmHidden) RoiManagerUtility.showManager();
			return;
		}
		
		// get merged image
		//maskImg.getWindow().setVisible(false);
		ImagePlus mergedImg = doRGBMerge(maskImg, doCalibration
				, rmSelection, fillRoi, transparency, colorVessel
				, colorSelection, visOption);
		maskImg.deleteRoi(); maskImg.setOverlay(null);
		String mergedTitle = maskImg.getShortTitle() + mergedImg.getTitle();
		if (WindowManager.getImage(mergedTitle) != null) {
			mergedTitle = WindowManager.getUniqueName(mergedTitle);
		}
		mergedImg.setTitle(mergedTitle);
		mergedImg.show();
		
		// do 3D if asked
		if (do3D) {
			do3DRendering(mergedImg, 2);
		}
		
		// display the input image in the end
		//maskImg.getWindow().setVisible(true);
		maskImg.show();
		
		// return original RoiManager, modify exist=true, append = false (overwrite)
		// revert original display mode: hide or show
		if (oriRois != null) {
			RoiManagerUtility.resetManager();
			RoiManagerUtility.roiArrayToManager(oriRois, true, false);
			RoiManagerUtility.setLocation(rmLocation);
			if (RoiManagerUtility.isOpen()) {
				if (rmHidden) RoiManagerUtility.hideManager();
				else RoiManagerUtility.showManager();
			}
		} else {
			RoiManagerUtility.showManager();
		}
	}
	
	
	public static void main(String[] args) {
		
		if (IJ.versionLessThan("1.52f")) System.exit(0);
		
		DefaultPrefService dps = new DefaultPrefService();
		filePath = dps.get(String.class, "persistedString", filePath);
		doCalibration = dps.getBoolean(Boolean.class, "persistedBoolean", doCalibration);
		getActiveRoi = dps.getBoolean(Boolean.class, "persistedBoolean", getActiveRoi);
		roiPath = dps.get(String.class, "persistedString", roiPath);
		fillRoi = dps.getBoolean(Boolean.class, "persistedBoolean", fillRoi);
		transparency = dps.getFloat(Float.class, "persistedFloat", transparency);
		visOption = dps.getInt(Integer.class, "persistedDouble", visOption);
		colorIndexVessel = dps.getInt(Integer.class, "persistedDouble", colorIndexVessel);
		colorIndexSelection = dps.getInt(Integer.class, "persistedDouble", colorIndexSelection);
		do3D = dps.getBoolean(Boolean.class, "persistedBoolean", do3D);

		String [] ij_args = 
			{ "-Dplugins.dir=C:/Fiji.app/plugins",
			"-Dmacros.dir=C:/Fiji.app/macros" };

		ImageJ.main(ij_args);
		CreateRoiMaskImage np = new CreateRoiMaskImage();
		np.run(null);
	}
}