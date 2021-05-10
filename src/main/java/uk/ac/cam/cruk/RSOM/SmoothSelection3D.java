package uk.ac.cam.cruk.RSOM;

import java.io.File;
import java.io.IOException;

import org.scijava.prefs.DefaultPrefService;

import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.Prefs;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.Roi;
import ij.plugin.Duplicator;
import ij.plugin.PlugIn;
import ij.plugin.filter.RankFilters;
import ij.plugin.frame.RoiManager;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;

import inra.ijpb.morphology.Morphology;
import inra.ijpb.morphology.Strel;

public class SmoothSelection3D implements PlugIn {

	protected static double smoothLv = 15;
	protected static double downSizeLv = 50;
	protected static double shrink = 0;

	public static ImagePlus managerToMask (
			) {
		RoiManager rm = RoiManager.getInstance2();
		if (rm == null) {
			IJ.log("RoiManager is not open, ROI smoothing will not be performed");
			return null;
		}
		rm.runCommand("Sort");
		//rm.runCommand(imp,"Interpolate ROIs");
		rm.runCommand("Interpolate ROIs");
		
		int nROI = rm.getCount();
		
		double maskWidth = 0;
		double maskHeight = 0;
		double maskDepth = 0;
		Roi r = null;

		for (int i=0; i<nROI; i++) {
			r = rm.getRoi(i);
			maskDepth = Math.max(r.getPosition(), maskDepth);
			maskWidth = Math.max(r.getBounds().x+r.getBounds().width, maskWidth);
			maskHeight = Math.max(r.getBounds().y+r.getBounds().height, maskHeight);
		}

		maskWidth = Math.ceil(maskWidth/10)*10;
		maskHeight = Math.ceil(maskHeight/10)*10;
		maskDepth = Math.ceil(maskDepth/10)*10;
		
		ImagePlus maskFromRoiManager = IJ.createImage("maskFromRoiManager", "8-bit black", (int)maskWidth, (int)maskHeight, (int)maskDepth);
		//ImagePlus maskFromRoiManager = IJ.createImage("maskFromRoiManager", "8-bit black", imp.getWidth(), imp.getHeight(), imp.getNSlices());
		for (int i=0; i<nROI; i++) {
			rm.select(i);
			rm.runCommand(maskFromRoiManager,"Fill");
		}
		return maskFromRoiManager;
	}
	
	public static void maskToRoiManager (
			ImagePlus mask,
			double enlargeDouble
			) {
		if (mask==null) {
			IJ.log("input image is not valid!");
			return;
		}
		int enlarge = (int)(Math.min(enlargeDouble, 255));
		RoiManagerUtility.resetManager();
		RoiManager rm = RoiManager.getInstance2();
		
		Prefs.blackBackground = true;
		boolean useInvertingLut = Prefs.useInvertingLut;
		Prefs.useInvertingLut = false;
		//IJ.run("Colors...", "foreground=white background=black selection=yellow");
		
		for (int i=1; i<mask.getNSlices()+1; i++) {
			mask.setSlice(i);
			
			//if (!Prefs.blackBackground)	mask.getProcessor().invertLut();
			
			IJ.run(mask, "Create Selection", "");
			IJ.run(mask, "Make Inverse", "");

			if (mask.getRoi()==null) {
				continue;
			}
			if (enlarge != 0) {
				int originalSize = mask.getRoi().getStatistics().pixelCount;
				IJ.run(mask, "Enlarge...", "enlarge="+enlarge+" pixel");
				if (enlarge < 0) {
					int enlargedSize = mask.getRoi().getStatistics().pixelCount;
					if (enlargedSize == originalSize) {	// shrink size large than area size, then delete the roi
						mask.deleteRoi();
					}
				}
			}
			if (mask.getRoi()==null) {
				continue;
			}
			rm.add(mask, mask.getRoi(), -1);
		}
		
		Prefs.useInvertingLut = useInvertingLut;
	}
	
	public static ImagePlus smoothSelection3D(
			ImagePlus imp,	//unsmoothed binary mask stack
			double smooth,
			double downSize,
			double shrinkSize // in pixel
			) {
		if (smooth==0) return imp;
		
		int[] Zrange = new int[2];
		double mdFilterSize = 30*smooth/100*(100-downSize)/100;
		
		//String title = imp.getTitle();
		ImagePlus impDup = new Duplicator().run(imp, 1, imp.getNSlices());
		//ImagePlus imp2 = imp.duplicate();
		//imp2.hide();
		
		IJ.run(impDup, "Fill Holes", "stack");
		
		int oriX = impDup.getWidth();
		int oriY = impDup.getHeight();
		//int oriC = impDup.getNChannels();
		int oriZ = impDup.getNSlices();
		//int oriT = impDup.getNFrames();
		

		int dsX = (int) Math.round(oriX*(100-downSize)/100);
		int dsY = (int) Math.round(oriY*(100-downSize)/100);
		int dsZ = (int) Math.round(oriZ*(100-downSize)/100);
		
		ImagePlus downsizeImp = null;
		if (downSize>0) {
			Zrange = findbound(impDup);
			downsizeImp = changeSize(impDup, dsX, dsY, dsZ, 1);
		} else {
			downsizeImp = impDup;
		}
		
		ImagePlus smoothXYZ = smoothSelectionCorners(downsizeImp, smooth);
		ImagePlus impXZY = transposeStack(smoothXYZ, "XYZ to XZY");
		//smoothXYZ.setTitle("smoothXYZ");
		//smoothXYZ.show();
		IJ.run(impXZY, "Median...", "radius="+String.valueOf(mdFilterSize)+" stack");
		ImagePlus smoothXZY = smoothSelectionCorners(impXZY, smooth);
		ImagePlus impZYX = transposeStack(smoothXZY, "XZY to ZYX");
		//smoothXZY.setTitle("smoothXZY");
		//smoothXZY.show();
		IJ.run(impZYX, "Median...", "radius="+String.valueOf(mdFilterSize)+" stack");
		ImagePlus smoothZYX = smoothSelectionCorners(impZYX, smooth);
		ImagePlus impXYZ = transposeStack(smoothZYX, "ZYX to XYZ");
		//smoothZYX.setTitle("smoothZYX");
		//smoothZYX.show();
		IJ.run(impXYZ, "Median...", "radius="+String.valueOf(mdFilterSize)+" stack");
		
		ImagePlus upsizeImp = null;
		if (downSize>0) {
			upsizeImp = changeSize(impXYZ, oriX, oriY, oriZ, (downSize/10));
			clearStack(upsizeImp, Zrange[0], Zrange[1]);
		}
		
		//impDup.setTitle("impDup");
		//impDup.show();
		//downsizeImp.setTitle("downsizeImp");
		//downsizeImp.show();
		
		impDup.changes = false;
		impDup.close();
		
		downsizeImp.changes = false;
		downsizeImp.close();
		
		smoothXYZ.changes = false; 
		smoothXYZ.close();
		
		impXZY.changes = false;
		impXZY.close();
		
		smoothXZY.changes = false; 
		smoothXZY.close();
		
		impZYX.changes = false; 
		impZYX.close();
		
		smoothZYX.changes = false;
		smoothZYX.close();
		
		impXYZ.changes = false; 
		impXYZ.close();
		
		System.gc();
		
		return upsizeImp;	
	}


	public static int[] findbound(
		ImagePlus imp
		) {
		int Z_begin = 0;
		int Z_end = 0;
		int nSlices = imp.getNSlices();
		double max = Double.MAX_VALUE;
		for (int i=1; i<nSlices+1; i++) {
			imp.setZ(i);
			max = imp.getRawStatistics().max;
			if (max==0) {
				if (Z_end==0) {
					Z_begin = i;
				}
			} else {
				Z_end = i;
			}
		}
		Z_begin += 1;	
		int[] Z_bound = {Z_begin, Z_end};	
		return Z_bound;
	}

	public static void clearStack(
			ImagePlus imp,
			int begin,
			int end
			) {
		int nSlices = imp.getNSlices();
		for (int i=1; i<nSlices+1; i++) {
			imp.setZ(i);
			if (i<begin) {
				IJ.run(imp, "Select All", "");
				IJ.run(imp, "Clear", "slice");
			} else if (i>end) {
				IJ.run(imp, "Select All", "");
				IJ.run(imp, "Clear", "slice");
			}
		}
	}

	public static ImagePlus transposeStack(
			ImagePlus imp,
			String transpose
			) {
		//imp.show();
		if (transpose.equals("XYZ to XZY")) {
			IJ.run(imp, "Reslice [/]...", "output=1.000 start=Top avoid");
		} else if (transpose.equals("XZY to ZYX")) {
			IJ.run(imp, "Reslice [/]...", "output=1.000 start=Left avoid");
		} else if (transpose.equals("ZYX to XYZ")) {
			IJ.run(imp, "Reslice [/]...", "output=1.000 start=Left rotate avoid");
		} else {
			return null;
		}
		ImagePlus reslicedImage = WindowManager.getImage("Reslice of "+imp.getTitle().split(" ")[0]);
		reslicedImage.hide();
		reslicedImage.setTitle(transpose);
		return reslicedImage;
	}

	// change size of the input binary image to new X, Y and Z
	// smooth the final result with a median filter to reduce pixelated effect
	public static ImagePlus changeSize(
			ImagePlus imp,
			double newX,
			double newY,
			double newZ,
			double mdRadius) {
		ImagePlus imp2 = imp.duplicate();
		IJ.run(imp2, "Size...", 
				" width="	+ String.valueOf(newX) +
				" height="	+ String.valueOf(newY) +
				" depth="	+ String.valueOf(newZ) +
				" constrain average interpolation=Bicubic");
		imp2 = IJ.getImage(); imp2.hide();
		imp2.getProcessor().setThreshold(1, 255, ImageProcessor.NO_LUT_UPDATE );
		ImageStack maskStack = new ImageStack(imp2.getWidth(), imp2.getHeight());
		for (int i=1; i<=imp2.getNSlices(); i++) {
			imp2.setSlice(i);
			ByteProcessor bp = imp2.createThresholdMask();
			maskStack.addSlice(String.valueOf(i),bp);
		}
		ImagePlus impMask = new ImagePlus("maskFromChangeSize",maskStack);
		
		RankFilters rf = new RankFilters();
		int z = impMask.getNSlices();
		for (int i=1; i<=z; i++) {
			impMask.setSlice(i);
			rf.rank(impMask.getProcessor(), mdRadius, RankFilters.MEDIAN);
		}
		imp2.close();
		return impMask;
	}
	
	public static ImagePlus smoothSelectionCorners(
			ImagePlus imp, 
			double smoothLevel
			) {
		
		ImageStack is = new ImageStack(imp.getWidth(), imp.getHeight(), imp.getNSlices());
		int nSlices = imp.getNSlices();
		for (int i=1; i<nSlices+1; i++) {
			imp.setZ(i);
			//getRawStatistics(nPixels, mean, min, maxA, std, histogram);
			double maxA = imp.getRawStatistics().max;
			if (maxA>0) {
				ImagePlus imp2 = new Duplicator().run(imp, i, i);
				IJ.run(imp2, "Distance Map", "");
				double maxB = imp2.getRawStatistics().max;
				double radiusMax = maxB-1;
				int radius = (int)(radiusMax*smoothLevel/100);
				//run("Morphological Filters", "operation=Opening element=Disk radius=["+radius+"]");
				Strel strel = Strel.Shape.DISK.fromRadius(radius);	// create structuring element (cube of radius 'radius')
				ImagePlus imp_opened = new ImagePlus("opened", Morphology.opening(imp2.getProcessor(), strel));

				imp_opened.getProcessor().setThreshold(1, 255, ImageProcessor.NO_LUT_UPDATE );
				ImagePlus newSlice = new ImagePlus("maskInSmoothSelectionCorners", imp_opened.getProcessor().createMask());
				
				String sliceLabel = imp.getStack().getSliceLabel(i);
				//imp.getStack().deleteSlice(i);
				//imp.getStack().addSlice(sliceLabel, newSlice.getProcessor(), i-1);
				is.deleteSlice(i);
				is.addSlice(sliceLabel, newSlice.getProcessor(), i-1);
				imp2.close();
				imp_opened.close();
			} else {
				is.deleteSlice(i);
				is.addSlice(String.valueOf(i), imp.getProcessor(), i-1);
			}
		}
		IJ.run("Collect Garbage", "");
		imp.deleteRoi();
		return (new ImagePlus("smoothed"+imp.getTitle(), is));
	}
	
	public static boolean addDialog() {
		
		GenericDialogPlus gd = new GenericDialogPlus("Smooth 3D selection");
		gd.addSlider("Smooth Level(%):", 0, 100, smoothLv, 5);
		gd.addSlider("Down-sizing Level (%):", 0, 80, downSizeLv, 1);
		gd.addSlider("Shrink-Enlarge (pixel)", -250, 250, shrink, 1);
		String html = "<html>"
		     +"<h2>Smooth 3D selection help</h2>"
		     +"1,	3D shape smooth level<br>"
		     +"	0% retains the shape while remove hairs.<br>"
		     +"	100% will smooth close to an ellipsoid.<br><br>"
		     +"2,	10% Down sizing means operation will be performed on<br>"
		     +"	a smaller stack 90% of the original stack on every dimension.<br>"
		     +"Down sizing will speed up the process drastically,<br>"
		     +"For instance 50% down sizing will take only 1/8 of the total computation time."
		     +"However it will be relatively less precise with more down-sizing.<br><br><br>";
		gd.addHelp(html);
		gd.showDialog();
		if (gd.wasCanceled())	return false;
		
		smoothLv = gd.getNextNumber();
		downSizeLv = gd.getNextNumber();
		shrink = gd.getNextNumber();
		return true;
		
	}
	
	public static void smoothSelectionWithRoiManager(
			//ImagePlus imp,
			double smoothLv, 
			double downSizeLv, 
			double shrink // shrink in pixel
			) {
		
		ImagePlus unsmoothedMask = managerToMask();
		if (unsmoothedMask==null) return;
		//unsmoothedMask.getCalibration().setUnit("micron");
		
		//IJ.log("debug SmoothSelection3D: shrinkSize: " + String.valueOf(shrink));
		
		if (shrink<0) {
			// THIS
			//double shrinkZ = shrink*(imp.getCalibration().getUnit().equals("pixel") ? 1 : 5);
			//double shrinkZ = shrink*5;
			//IJ.log("debug SmoothSelection3D: shrinkZ: " + String.valueOf(shrinkZ));
			//IJ.log("debug SmoothSelection3D: imp size: " + String.valueOf(unsmoothedMask.getStack().getSize()));
			
			int zBegin = RoiManager.getInstance2().getRoi(0).getZPosition();
			//IJ.log("debug SmoothSelection3D: (int)(1-shrinkZ): " + String.valueOf((int)(zBegin-shrink*5)));
			clearStack(unsmoothedMask, (int)(zBegin-shrink), unsmoothedMask.getNSlices());
		}
		
		
		ImagePlus smoothedMask = smoothSelection3D(unsmoothedMask, smoothLv, downSizeLv, shrink);
		//unsmoothedMask.show();
		//smoothedMask.show();
		maskToRoiManager(smoothedMask, shrink);
		
		//RoiManagerUtility.hideManager();
		unsmoothedMask.changes = false;
		unsmoothedMask.close();
		if (smoothedMask!=null) {
			smoothedMask.changes = false;
			smoothedMask.close();
		}
		
		RoiManager rm = RoiManager.getInstance();
		if (rm==null || rm.getCount()==0) {
			IJ.error("Smooth/Shrink parameter wrong, No ROI left after smooth/shrink!");
		}
		IJ.run("Collect Garbage", "");
		
	}
	
	public static void batchRun (
			File[] fileArray
			) throws IOException {

		// check 3rd party pluginhjn
		boolean installMorpholibJ = false;
		if (!PluginUtility.pluginCheck("MorphoLibJ", null)) {
			installMorpholibJ = PluginUtility.installMorpholibJ();
			if (!installMorpholibJ) {
				IJ.log("   \"IJPB plugin\" not found!");
				IJ.log("   Install it manually by adding IJPB-plugins to update sites of Fiji.");
				return;
			}
		}
		// Ask User to restart Fiji after newly installed plugin
		if (installMorpholibJ) {
			IJ.log("   3rd party plugin installed.");
			IJ.log("   Restart Fiji to run the RSOM analysis plugin.");
			return;
		}
		if (!addDialog())	return;
		//double start = System.currentTimeMillis();
		// prepare RoiManager for batch processing
		// prepare RoiManager for operation
		RoiManager rm = RoiManager.getInstance2();
		if (rm == null) rm = new RoiManager();
		else rm.reset();
		rm.setVisible(false);
		
		String date = GetDateAndTime.getCurrentDate();
		String time = GetDateAndTime.getCurrentTime();
		IJ.log("\n\nBatch Smooth 3d Selection executed at:");
		IJ.log(date + " " + time);
		
		RsomImageStack RIS;
		// process through the file list			
		for (File f : fileArray) {
			IJ.log(" Processing File: " + f.getName());
			//long start = GetDateAndTime.getCurrentTimeInMs();
			double start = System.currentTimeMillis();
			
			RIS = new RsomImageStack(f.getCanonicalPath());
			//ImagePlus inputImg = RIS.RSOMImg;
			//inputImg = RIS.RSOMImg;
			String roiPath = RIS.manualROIPath;
			if (!new File(roiPath).exists()) {
				IJ.log(" Manual ROI does not exist yet, skip current File.");
				continue;
			}
			rm.runCommand("Open", RIS.manualROIPath);
			rm.runCommand(RIS.RSOMImg,"Sort");
			rm.runCommand(RIS.RSOMImg,"Interpolate ROIs");
			
			smoothSelectionWithRoiManager(smoothLv, downSizeLv, shrink);
			
			rm.runCommand("Save", RIS.selectionPath);
			rm.reset();
			System.gc();
			//RoiManagerUtility.showManager();
			double end = System.currentTimeMillis();
			double duration = (end-start)/1000;
			IJ.log(" 3D selection smoothing took " + String.valueOf(duration) + " second.\n");
		}
	
	}
	
	@Override
	public void run(String arg0) {
		
		// check 3rd party pluginhjn
		boolean installMorpholibJ = false;
		if (!PluginUtility.pluginCheck("MorphoLibJ", null)) {
			installMorpholibJ = PluginUtility.installMorpholibJ();
			if (!installMorpholibJ) {
				IJ.log("   \"IJPB plugin\" not found!");
				IJ.log("   Install it manually by adding IJPB-plugins to update sites of Fiji.");
				return;
			}
		}
		// Ask User to restart Fiji after newly installed plugin
		if (installMorpholibJ) {
			IJ.log("   3rd party plugin installed.");
			IJ.log("   Restart Fiji to run the RSOM analysis plugin.");
			return;
		}
				
		// take current RoiManager as input
		double start = System.currentTimeMillis();
		
		if (!addDialog())	return;
		
		/*
		ImagePlus unsmoothedMask = managerToMask();
		if (unsmoothedMask==null) return;
		ImagePlus smoothedMask = smoothSelection3D(unsmoothedMask, smoothLv, downSizeLv, 0);
		//smoothedMask.setTitle("smoothed from plugin");
		//smoothedMask.show();
		maskToRoiManager(smoothedMask, shrink);
		smoothedMask.changes = false;
		smoothedMask.close();
		*/
		RoiManagerUtility.hideManager();
		smoothSelectionWithRoiManager(smoothLv, downSizeLv, shrink);
		System.gc();
		RoiManagerUtility.showManager();
		
		double end = System.currentTimeMillis();
		double duration = (end-start)/1000;
		IJ.log("3D selection smoothing took " + String.valueOf(duration) + " second.\n");
		// returns another RoiManager
	}
	
	public static void main(String[] args) {
		
		DefaultPrefService prefs = new DefaultPrefService();
		smoothLv = prefs.getDouble(Double.class, "RSOM-smoothLv", smoothLv);
		downSizeLv = prefs.getDouble(Double.class, "RSOM-downSizeLv", downSizeLv);
		shrink = prefs.getDouble(Double.class, "RSOM-shrink", shrink);
		
		SmoothSelection3D sst = new SmoothSelection3D();
		sst.run(null);
		
		prefs.put(Double.class, "RSOM-smoothLv", smoothLv);
		prefs.put(Double.class, "RSOM-downSizeLv", downSizeLv);
		prefs.put(Double.class, "RSOM-shrink", shrink);
		
	}
	
}
