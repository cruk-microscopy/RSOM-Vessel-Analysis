package uk.ac.cam.cruk.RSOM;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.Calibration;
import ij.process.StackStatistics;
import ij.WindowManager;


public class LocalThicknessAnalysis {

	protected static ImagePlus mask;
	protected static String method;
	protected final String defaultMethod = "default";
	protected static String imageName;
	protected static Calibration cal;
	
	public LocalThicknessAnalysis( // construct is not optimised.
			ImagePlus mask,
			Calibration cal,
			String method,
			String imageName
			) {
		if (method!= defaultMethod) {
			IJ.log("New method for computing image local thickness"
					+ "is still yet to be implemented." + '\n'
					+ "Please use ImageJ default method for the moment.");
			return;
		} else {
			LocalThicknessAnalysis.mask = mask;
			LocalThicknessAnalysis.mask.setCalibration(cal);
			LocalThicknessAnalysis.method = method;
			LocalThicknessAnalysis.imageName = imageName;
			return;
		}
	}
	
	public static ImagePlus getThicknessMap (ImagePlus imp) {
		if (imp == null) return null;
		ImagePlus locThkImg = null;
		Boolean silent = true;
		if (silent) {
			// !!! silent mode does not return system memory taken by:
			// distance map, distance ridge, local thickness !!! 
			IJ.run(imp, "Local Thickness (masked, calibrated, silent)", "");
			String locThkName = imp.getShortTitle()+"_LocThk";
			locThkImg = WindowManager.getImage(locThkName);
			locThkImg.getWindow().setVisible(false);
			locThkImg.setTitle(imageName+"_diameterMap");
		} else {
			IJ.run(imp, "Local Thickness (complete process)", "threshold=1");
			String locThkName = imp.getShortTitle()+"_LocThk";
			locThkImg = WindowManager.getImage(locThkName);
			locThkImg.getWindow().setVisible(false);
			locThkImg.setTitle(imageName+"_diameterMap");
			// update pixel value in diameter map to calibrated unit
			final double pixelWidth = imp.getCalibration().pixelWidth;
			final ImageStack stack = locThkImg.getStack();
			final int depth = stack.getSize();
			for (int z = 1; z <= depth; z++) {
				stack.getProcessor(z).multiply(pixelWidth);
			}
			final StackStatistics stackStatistics = new StackStatistics(locThkImg);
			final double maxPixelValue = stackStatistics.max;
			locThkImg.getProcessor().setMinAndMax(0, maxPixelValue);
		}
		return locThkImg;
	}
	
	public static ImagePlus run() {
		if (mask == null) return null;
		IJ.run(mask, "Local Thickness (masked, calibrated, silent)", "");
		String locThkName = mask.getShortTitle()+"_LocThk";
		ImagePlus locThkImg = WindowManager.getImage(locThkName);
		locThkImg.hide();
		locThkImg.setTitle(imageName+"_diameterMap");
		return locThkImg;
	}
		
}
