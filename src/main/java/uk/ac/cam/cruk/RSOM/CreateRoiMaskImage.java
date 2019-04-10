package uk.ac.cam.cruk.RSOM;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Point;
import java.io.File;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JProgressBar;
import javax.swing.border.Border;

import org.scijava.prefs.DefaultPrefService;
import org.scijava.vecmath.Color3f;

import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.WindowManager;
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
	protected static String roiPath1 = "or use current ROI Manager";
	protected static String roiPath2 = "or use current ROI Manager";
	protected static Boolean fillRoi = true;
	protected static float transparency;
	protected RoiManager rmSelection;
	protected static Boolean getActiveRoi = false;
	protected static int visOption = 2;
	protected static String[] Colors = {"RED", "YELLOW", "GREEN", "BLUE",
	"MAGENTA", "PINK", "CYAN", "ORANGE", "WHITE", "BLACK", "GRAY"};
	protected static int colorIndexVessel = 0;
	protected static int colorIndexSelection1 = 1;
	protected static int colorIndexSelection2 = 6;
	protected static Color colorVessel;
	protected static Color colorSelection1;
	protected static Color colorSelection2;
	protected static Boolean do3D = false;
	protected static Boolean doCalibration = false;
	protected int activeImgCount = WindowManager.getImageCount();
	protected int[] wList = WindowManager.getIDList();
	protected String[] imgTitles;
	protected boolean getActiveImage = false;
	protected int activeImgNum = -1;
	
	public String[] RoiManagerList() {
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
		titles[0] = "Use current ROI Manager";
		System.arraycopy(WindowManager.getImageTitles(), 0, titles, 1, numOpenImg);
		return titles;
	}
	
	public ImagePlus addDialog() {
		GenericDialogPlus gd = new GenericDialogPlus("3D selection check");
		if (activeImgCount!=0) {
			String [] imgTitles = activeImageList();
			gd.addChoice("Get active image", imgTitles, imgTitles[activeImgCount]);
		}
		gd.addDirectoryOrFileField("Open image on disk", filePath);
		gd.setInsets(0,137,0);
		gd.addCheckbox("Use image calibration", doCalibration);
		//gd.setInsets(20,168,0);
		//gd.addCheckbox("Use current ROI Manager", getActiveRoi);
		
		gd.addFileField("Load ROI from disk", "or use current ROI Manager");
		gd.addFileField("Load ROI from disk", roiPath2);
		
		gd.setInsets(0,137,0);
		gd.addCheckbox("Fill ROI", fillRoi);
		//gd.addToSameRow();
		gd.addSlider("opaque - transparent", 0, 100, transparency);
		//gd.addNumericField("transparency:", transparency, 1, 3, "%");
		String[] visOptions = {"RGB merged","selection as Overlay"
				, "multi-channel composite"};
		gd.setInsets(20,0,5);
		gd.addChoice("Visualization option", visOptions, visOptions[visOption]);
		gd.addChoice("Color of vessel image:", Colors, Colors[colorIndexVessel]);
		gd.addChoice("Color ROI 1:", Colors, Colors[colorIndexSelection1]);
		gd.addChoice("Color ROI 2:", Colors, Colors[colorIndexSelection2]);
		gd.setInsets(0,137,0);
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
		//getActiveRoi = gd.getNextBoolean();
		//boolean doRoi1 = gd.getNextBoolean();
		roiPath1 = gd.getNextString();
		roiPath2 = gd.getNextString();
		
		fillRoi = gd.getNextBoolean();
		transparency = (float)gd.getNextNumber();
		visOption = gd.getNextChoiceIndex();
		colorIndexVessel = gd.getNextChoiceIndex();
		colorIndexSelection1 = gd.getNextChoiceIndex();
		colorIndexSelection2 = gd.getNextChoiceIndex();
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
		colorSelection1 = getColorFromString(Colors[colorIndexSelection1]);
		colorSelection2 = getColorFromString(Colors[colorIndexSelection2]);
		
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
	
	public static Roi[] modify3DSelection (
			ImagePlus imp,
			Roi[] inputRoi,
			Boolean fill,
			Color selectionColor,
			Float alpha
			) {
		
		if (inputRoi == null)	return null;
		
		RoiManager rm = RoiManager.getInstance2();
		if (rm==null) rm = new RoiManager();
		rm.reset();
		rm.setVisible(false);
		
		RoiManagerUtility.roiArrayToManager(inputRoi, false);
		for (int i=0; i<rm.getCount(); i++) {
			rm.select(imp, i);
			rm.getRoi(i).setPosition(imp);
		}
		
		if (fill) {
			Color fillColor = new Color(
					selectionColor.getColorSpace(),
					selectionColor.getRGBColorComponents(null),
					alpha); // alpha level 1.0 = opaque
			int nROI = rm.getCount();
			int sliceStep = 0; int currentSlice = 0;
			for (int i=0; i<nROI; i++) {
				rm.select(imp, i);
				sliceStep = imp.getCurrentSlice() - currentSlice;
				currentSlice = imp.getCurrentSlice();
				if (sliceStep>1 || i==0 || i==(nROI-1)) {
				//if (sliceStep!=1) {	// trick to detect non-continuous slice to fill
					rm.getRoi(i).setFillColor(fillColor);
				}
			}
		}
		
		for (int i=0; i<rm.getCount(); i++) {
			rm.select(imp, i);
			rm.getRoi(i).setPosition(imp);
		}
		Roi[] roiArray = rm.getRoisAsArray();
		rm.reset();
		return roiArray;
	}
	
	public ImagePlus doRGBMerge(
			ImagePlus vessel,
			Boolean calibrate,
			Roi[] inputROI1,	//null for empty channel
			Roi[] inputROI2,	//null for empty channel
			Boolean fill,
			Float trans,
			Color colorVessel,
			Color colorSelection1,
			Color colorSelection2,
			int visOption
			) {
		
		Float alpha = 1-trans/100;
		
		Roi[] modifiedROI1 = modify3DSelection(vessel, inputROI1, fill, colorSelection1, alpha);
		Roi[] modifiedROI2 = modify3DSelection(vessel, inputROI2, fill, colorSelection2, alpha);
		
		//int nROI = rm.getCount();
		// if (!getActiveRoi) rm.setVisible(false);
		
		vessel.deleteRoi();
		//Duplicator dp = new Duplicator();
		ImagePlus vesselChannel = new Duplicator().run(vessel);
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
		
		new StackConverter(vesselChannel).convertToGray8();
		LUT lutVessel = LUT.createLutFromColor(colorVessel);
		vesselChannel.setLut(lutVessel);
		
		int [] dim = vesselChannel.getDimensions();
		Color roiDefaultColor = Roi.getColor();
		RoiManager rm = RoiManager.getInstance2();
		if (rm==null) rm = new RoiManager();
		
		
		switch (visOption) {
		
		case 0: //RGB
			/*	Use RoiManager to create RGB image
			 *  will not work with 3D convex hull;
			 *  Require further inspection:
			 *  Known problem:
			 *  	RoiManager.draw() only draw outline without fill;
			 *  	imp.flattenStack() has bug with RGB stack;
			 *  	imp.flatten() need updateImage on every slice; 
			 *  
			 * Make use of overlay as intermediate stack to
			 * create RGB stack. Use imp.flatten() to generate
			 * RGB slice-wise and append to new RGB stack
			 */
			if (modifiedROI1!=null) {
				Roi.setColor(colorSelection1);
				RoiManagerUtility.roiArrayToManager(modifiedROI1, false); // modify exist, not append
				rm = RoiManager.getInstance2();
				rm.moveRoisToOverlay(vesselChannel);
				vesselChannel.getOverlay().setStrokeColor(colorSelection1);
			}
			if (modifiedROI2 !=null) {
				Roi.setColor(colorSelection2);
				RoiManagerUtility.roiArrayToManager(modifiedROI2, false); // modify exist, not append
				rm = RoiManager.getInstance2();
				rm.moveRoisToOverlay(vesselChannel);
				vesselChannel.getOverlay().setStrokeColor(colorSelection2);
			}
			Roi.setColor(roiDefaultColor);
			new StackConverter(vesselChannel).convertToRGB();
			
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
			//rgb.show();
			return rgb;
		case 1:	//selection as overlay
			
			if (modifiedROI1!=null) {
				Roi.setColor(colorSelection1);
				RoiManagerUtility.roiArrayToManager(modifiedROI1, false); // modify exist, not append
				rm = RoiManager.getInstance2();
				rm.moveRoisToOverlay(vesselChannel);
				vesselChannel.getOverlay().setStrokeColor(colorSelection1);
			}
			if (modifiedROI2!=null) {
				Roi.setColor(colorSelection2);
				RoiManagerUtility.roiArrayToManager(modifiedROI2, false); // modify exist, not append
				rm = RoiManager.getInstance2();
				rm.moveRoisToOverlay(vesselChannel);
				vesselChannel.getOverlay().setStrokeColor(colorSelection2);
			}
			Roi.setColor(roiDefaultColor);
			
			vesselChannel.setTitle("_Overlay");
			return vesselChannel;
		case 2: //composite
			
			ImagePlus selectionChannel1 = createSelectionImageStack(vessel, calibrate, fill, alpha, colorSelection1, modifiedROI1);
			ImagePlus selectionChannel2 = createSelectionImageStack(vessel, calibrate, fill, alpha, colorSelection2, modifiedROI2);	
			
			vesselChannel.setLut(lutVessel);
			
			ImagePlus multiChannelComposite = RGBStackMerge.mergeChannels(
					new ImagePlus[]{vesselChannel, selectionChannel1, selectionChannel2}, false);

			if (calibrate) {
				multiChannelComposite.setCalibration(vessel.getCalibration());
			}
			vesselChannel.close(); //vesselChannel.flush();
			if (selectionChannel1!=null) selectionChannel1.close(); //selectionChannel.flush();
			if (selectionChannel2!=null) selectionChannel2.close();
			
			Roi.setColor(roiDefaultColor);
			
			multiChannelComposite.setTitle("_Composite");
			return multiChannelComposite;
		}
		return null;
	}
	
	public ImagePlus createSelectionImageStack (
			ImagePlus imp,
			Boolean calibrate,
			Boolean fill,
			double alpha,
			Color colorSelection,
			Roi[] roiArray
			) {
		if (roiArray==null) return null;
		
		int [] dim = imp.getDimensions();
		ImagePlus selectionChannel = NewImage.createByteImage(
				"selectionChannel", dim[0], dim[1], dim[3], NewImage.FILL_BLACK);
		if (calibrate) {
			selectionChannel.setCalibration(imp.getCalibration());
		}
		Toolbar.setForegroundColor(Color.WHITE);
		
		
		RoiManager rm = RoiManager.getInstance2();
		rm.reset();
		RoiManagerUtility.roiArrayToManager(roiArray, false); // modify exist, not append

		// above 3 lines have bug preventing selection to be displayed on each slice
		/*
		for (int i=0; i<rm.getCount(); i++) {
			if (rm.getRoi(i).getFillColor() != null) {
				int fillValue = (int)(255 * alpha);
				selectionChannel.getProcessor().setColor(fillValue);
				selectionChannel.getProcessor().fill(rm.getRoi(i));
			}
			rm.runCommand(selectionChannel,"Draw");
		}
		*/
		int step = 0; int currentSlice = 0;
		for (int i=0; i<rm.getCount(); i++) {
			rm.select(selectionChannel, i);
			step = selectionChannel.getCurrentSlice() - currentSlice;
			currentSlice = selectionChannel.getCurrentSlice();
			if (step>1 || i==0 || i==rm.getCount()-1) {
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
		return selectionChannel;
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
		
		Roi[] originalRoiArray = null;
		boolean managerOpen = true;
		RoiManager rm = RoiManager.getInstance2();
		if (rm==null) {
			managerOpen = false;
			rm = new RoiManager();
		} else {
			originalRoiArray = rm.getRoisAsArray();
		}
		Point rmLocation = rm.getLocation();
		rm.setVisible(false);
		rm.reset();
		
		Roi[] selection1 = null;
		Roi[] selection2 = null;
		// check first ROI entry
		if (roiPath1!=null && roiPath1.length()!=0) {
			if (roiPath1.equals("or use current ROI Manager")) {
				selection1 = originalRoiArray;
			} else {
				rm.runCommand("Open",roiPath1);
				// code to make sure the selection is correctly associated with a slice position on the image
				for (int i=0; i<rm.getCount(); i++) {
					rm.select(maskImg, i);
					rm.getRoi(i).setPosition(maskImg);
				}
				selection1 = rm.getRoisAsArray();
			}
		}
		rm.reset();
		if (roiPath2!=null && roiPath2.length()!=0) {
			if (roiPath2.equals("or use current ROI Manager")) {
				selection2 = originalRoiArray;
			} else {
				rm.runCommand("Open",roiPath2);
				// code to make sure the selection is correctly associated with a slice position on the image
				for (int i=0; i<rm.getCount(); i++) {
					rm.select(maskImg, i);
					rm.getRoi(i).setPosition(maskImg);
				}
				// code to make sure the selection is correctly associated with a slice position on the image
				selection2 = rm.getRoisAsArray();
			}
		}
		
		// get merged image
		ImagePlus mergedImg = doRGBMerge(maskImg, doCalibration
				, selection1, selection2, fillRoi, transparency, colorVessel
				, colorSelection1, colorSelection2, visOption);
		
		maskImg.deleteRoi(); maskImg.setOverlay(null);
		String mergedTitle = maskImg.getShortTitle() + mergedImg.getTitle();
		if (WindowManager.getImage(mergedTitle) != null) {
			mergedTitle = WindowManager.getUniqueName(mergedTitle);
		}
		mergedImg.setTitle(mergedTitle);
		mergedImg.show();
		ImageJ j = IJ.getInstance();
		mergedImg.getWindow().setLocation(j.getX()+100,j.getY()+j.getHeight()+20);	
		//mergedImg.show();
		
		// do 3D if specified
		if (do3D) {
			do3DRendering(mergedImg, 2);
		}
		
		// treat the input image and RoiManager in the end
		if (getActiveImage) {
			maskImg.deleteRoi();
			maskImg.changes = false;
			maskImg.show();
		}
		else 
			maskImg.close();
		if (managerOpen) {
			rm.reset();
			RoiManagerUtility.roiArrayToManager(originalRoiArray, false);
			rm.setLocation(rmLocation);
			rm.setVisible(true);
		} else {
			rm.close();
		}
	}
	
	
	public static void main(String[] args) {
		
		if (IJ.versionLessThan("1.52f")) System.exit(0);
		
		DefaultPrefService dps = new DefaultPrefService();
		filePath = dps.get(String.class, "persistedString", filePath);
		doCalibration = dps.getBoolean(Boolean.class, "persistedBoolean", doCalibration);
		//getActiveRoi = dps.getBoolean(Boolean.class, "persistedBoolean", getActiveRoi);
		roiPath2 = dps.get(String.class, "persistedString", roiPath2);
		fillRoi = dps.getBoolean(Boolean.class, "persistedBoolean", fillRoi);
		transparency = dps.getFloat(Float.class, "persistedFloat", transparency);
		visOption = dps.getInt(Integer.class, "persistedDouble", visOption);
		colorIndexVessel = dps.getInt(Integer.class, "persistedDouble", colorIndexVessel);
		colorIndexSelection1 = dps.getInt(Integer.class, "persistedDouble", colorIndexSelection1);
		colorIndexSelection2 = dps.getInt(Integer.class, "persistedDouble", colorIndexSelection2);
		do3D = dps.getBoolean(Boolean.class, "persistedBoolean", do3D);

		String [] ij_args = 
			{ "-Dplugins.dir=C:/Fiji.app/plugins",
			"-Dmacros.dir=C:/Fiji.app/macros" };

		ImageJ.main(ij_args);
		CreateRoiMaskImage np = new CreateRoiMaskImage();
		np.run(null);
		
		dps.put(String.class, "persistedString", filePath);
		dps.put(Boolean.class, "persistedBoolean", doCalibration);
		dps.put(String.class, "persistedString", roiPath2);
		dps.put(Boolean.class, "persistedBoolean", fillRoi);
		dps.put(Float.class, "persistedFloat", transparency);
		dps.put(Integer.class, "persistedDouble", visOption);
		dps.put(Integer.class, "persistedDouble", colorIndexVessel);
		dps.put(Integer.class, "persistedDouble", colorIndexSelection1);
		dps.put(Integer.class, "persistedDouble", colorIndexSelection2);
		dps.put(Boolean.class, "persistedBoolean", do3D);
	}
}