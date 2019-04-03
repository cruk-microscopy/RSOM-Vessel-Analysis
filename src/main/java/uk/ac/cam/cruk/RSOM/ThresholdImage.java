package uk.ac.cam.cruk.RSOM;

import java.util.Arrays;

import ij.gui.WaitForUserDialog;
import ij.gui.YesNoCancelDialog;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;

import ij.plugin.frame.ThresholdAdjuster;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.process.StackStatistics;

import java.text.DecimalFormat;

public class ThresholdImage {

	protected ImagePlus currentActiveImage;
	protected boolean silentMode = true;
	protected ImagePlus mask;
	//protected ImageStack maskStack;
	protected int x, y, z;
	protected String method;
	protected int methodID;
	protected double[] bounds = {0, 0};
	protected double max;
	final String[] methods = new String[] {"Default","Huang","Intermodes","IsoData",
			"IJ_IsoData","Li","MaxEntropy","Mean","MinError","Minimum","Moments",
			"Otsu","Percentile","RenyiEntropy","Shanbhag","Triangle","Yen","Manual"};
	
	
	public void run(
			ImagePlus imp,
			String method,
			boolean silentMode
			) {
		
		if (imp == null)	return;
		ThresholdImage t = setParameters(imp, method, silentMode);
		if (t == null) return;
		t.bounds = t.autoThresholdStack(t.currentActiveImage, t.method);
		t.mask = makeMaskStack(t.currentActiveImage, t.method, t.bounds);
		while (checkThreshold(t)) {
			IJ.log("Change method or manually define new threshold.");
			t.bounds = t.autoThresholdStack(t.currentActiveImage, "Manual");
			t.mask = makeMaskStack(t.currentActiveImage, t.method, t.bounds);
		}
		return;
	}

	public ThresholdImage setParameters (
			ImagePlus inputImp, 
			String inputMethod,
			boolean inputSilentMode
			) {
		if (inputImp == null) {
			return null;
		} else {
			currentActiveImage = inputImp;
		}
		silentMode = inputSilentMode;
		int[] dim = inputImp.getDimensions();
		x = dim[0]; y = dim[1]; z = dim[3];
		
		StackStatistics ss = new StackStatistics(inputImp);
		max = ss.max;
		bounds[0] = ss.min;	bounds[1] = ss.max;
		methodID = Arrays.asList(methods).indexOf(inputMethod);
		if (methodID == -1) methodID = 11;	//default method is Otsu
		method = methods[methodID];
		return this;
	}
	
	public double[] manualThresholdStack (
			ImagePlus imp,
			boolean silentMode
			) {
		if (imp == null) imp = this.currentActiveImage;
		imp.show();
		int xi = imp.getWindow().getBounds().x;
		int yi = imp.getWindow().getBounds().y;
		int wi = imp.getWindow().getBounds().width;
		
		// display threshold and user dialog window, wait for user input
		ThresholdAdjuster t = new ThresholdAdjuster();
		t.setLocation(xi+wi+10,yi+120);
		WaitForUserDialog w = new WaitForUserDialog("Threshold","set a threshold");
		w.setBounds(xi+wi+10,yi,200,100);
		w.show();
		t.close();
		
		bounds[0] = imp.getProcessor().getMinThreshold();
		if (imp.getProcessor().getMaxThreshold()<max) {
			YesNoCancelDialog y = new YesNoCancelDialog(IJ.getInstance(),"","modify threshold upper bound?");
			y.setBounds(xi+wi+10,yi,200,100);
			if (y.yesPressed() == true) {
				bounds[1] = imp.getProcessor().getMaxThreshold();
			}
		} else {
			bounds[1] = max;
		}
		method = "Manual";
		if (silentMode)	imp.hide();
		return bounds;
	}
	
	public double[] autoThresholdStack (
			ImagePlus imp,
			String method
			) {
		if (imp == null) imp = this.currentActiveImage;
		if (method == null) method = this.method;
		if (method == "Manual") return manualThresholdStack(imp, silentMode);
		IJ.setAutoThreshold(imp, method+" dark no-reset stack");
		bounds[0] = imp.getProcessor().getMinThreshold();
		bounds[1] = imp.getProcessor().getMaxThreshold();
		return bounds;
	}
	
	public boolean checkThreshold (ThresholdImage t) {
		// upper bound should be larger than 0
		if (t.bounds[1] == 0)	{
			IJ.log("Error: thresholding method or value wrong.");
			return true;
		}
		// mask image has to be 8-bit binary image
		mask = t.mask;
		ImageStatistics stats = mask.getStatistics();
		if ((mask.getBitDepth() != 8) || 
				(stats.histogram[0]+stats.histogram[255]!=stats.pixelCount)) {
			IJ.log("Error: thresholded image has to be 8-bit binary image.");
			return true;
		}
		// thresholded pixel count should be in acceptable range
		double[] criterion = {0.0001, 0.15}; // the volume fraction of blood vessel
		if (t.mask==null)	{
			return true;
		}
		StackStatistics ssm = new StackStatistics(t.mask);
		double volCount = ssm.pixelCount-ssm.maxCount;
		double volFraction = volCount/ssm.maxCount;
		if (volFraction < criterion[0]) {
			IJ.log("Error: thresholded voxel less than 0.01%");
			return true;
		}
		if (volFraction > criterion[1]) {
			IJ.log("Error: thresholded voxel more than 15%");
			return true;
		}
		return false;
	}
	
	public ImagePlus makeMaskStack (
			ImagePlus imp,
			String method,
			double[] bounds
			) {
		if (imp == null) imp = this.currentActiveImage;
		int x = imp.getWidth(); int y = imp.getHeight(); int z = imp.getNSlices();
		imp.getProcessor().setThreshold(bounds[0],bounds[1],ImageProcessor.NO_LUT_UPDATE);
		ImageStack maskStack = new ImageStack(x, y);
		String sliceLabel = method 
				+ " [" + String.valueOf(bounds[0]) 
				+ ", " + String.valueOf(bounds[1]) 
				+ "]";
		for (int i=0; i<z; i++) {
			imp.setSlice(i+1);
			ByteProcessor bp = imp.createThresholdMask();
			//maskStack.addSlice("",bp);
			maskStack.addSlice(sliceLabel,bp);
		}
		IJ.resetThreshold(imp);
		mask = new ImagePlus("mask",maskStack);
		return mask;
	}
	

}
