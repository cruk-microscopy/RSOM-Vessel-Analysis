package uk.ac.cam.cruk.RSOM;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import org.scijava.prefs.DefaultPrefService;

import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import ij.gui.GenericDialog;
import ij.plugin.CanvasResizer;
import ij.plugin.PlugIn;
import ij.plugin.ZProjector;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.StackStatistics;
import ij.util.ThreadUtil;

public class CreateHomogeneityMap implements PlugIn {

	/*
	 *  Create Heterogeneity/Homogeneity map based on Density convolution
	 *  
	 *  Use FFT convolution from imglib2, to compensate from border effect, it need to
	 *  first zero padding the image stack in all 6 axis: Front, Back, Up, Down, Left,
	 *  Right. For an input image I with dimensions X, Y, Z, to convolve
	 *  with a kernel of arbitrary shape but minimum bounding cube with side length R, the
	 *  padded image stack Ip will have dimensions X+2R, Y+2R, Z+2R.
	 *  
	 *  The procedure to get the density convolution is as following:
	 *  1,	check and convert input image I to 8-bit or 8-bit binary;
	 *  2,	zero pad I(X,Y,Z) to Ip(X+2R, Y+2R, Z+2R);
	 *  3,	construct kernel image K: cube / sphere;
	 *  4,	convert both Ip and K to imglib2 image, float type;
	 *  5,	normalize the kernel so it sum up to 1;
	 *  6,	convolve Ip with normalized kernel K, (in site);
	 *  7,	post-process the convolved image:
	 *  		- crop out the center so it has the same dimension as I;
	 *  		- regulate the minimum and maximum value;
	 *  
	 *  
	 *  For heterogeneity measure, several statistical parameters can be select as candidate:
	 *  	Mathmatical Moment at differet order:
	 *  	2nd, Variance; 3rd, Skewness; 4th, Kurtosis
	 *  	Standard Deviation: square root of variance, correct for sample size (N-1)
	 *  	Coefficient Variance: standard deviation normalized by mean
	 *  	Range: difference between maximum and minimum value in local volume (kernel)
	 *  	Entropy: Shannon entropy based on 256 bin histogram
	 *  For binary input, the above parameters can be extracted faster, since the input data
	 *  simply forms a Bernoulli Distribution, and a histogram with 2 bins: 0 and 1. 
	 *  	
	 *  Author: Ziqiang Huang <Ziqiang.Huang@cruk.cam.ac.uk>	
	 *  Date: 2019.08.27
	 *  Version: 1.0
	 *  Known issues:
	 *  	- float precision returns some small negative values (in the range of 10E-6)
	 */
	protected ImagePlus activeImage;
	
	protected final String[] mapTypes = {"homogeneous map", "heterogeneous map"};
	protected final String[] kernelShapes = {"cube", "ball"};
	protected final String[] heteroParams = {"entropy", "variance", "stdDev", "relative stdDev", "range", "skewness"};
	protected int radius;

	protected int type = 0;
	protected int shape = 0;
	protected int param = 0;
	protected double size = 20;
	
	protected boolean doOval = false;
	protected boolean doSmooth = false;

	protected int kernelSize; // size (diameter) of the kernel
	protected float T;	// total pixel count inside the 3D kernel
	protected int bufferWidth;	//the same as padded width
	protected int bufferHeight;	//the same as padded height
	
	
	@Override
	public void run(String arg) {
		
		/*
		// get kernel configuration
		DefaultPrefService prefs = new DefaultPrefService();
		radius = prefs.getInt(Integer.class, "RSOM-den3D-radius", radius);
		//doOval = prefs.getBoolean(Boolean.class, "RSOM-den3D-doOval", doOval);
		//doSmooth = prefs.getBoolean(Boolean.class, "RSOM-den3D-doSmooth", doSmooth);
		GenericDialog gd = new GenericDialog("configure density map");
		gd.addNumericField("kernel radius", radius, 0);
		//gd.addChoice("kernel shape", kernelShape, kernelShape[doOval?1:0]);
		//gd.addCheckbox("smooth map", doSmooth);
		gd.showDialog();
		if (gd.wasCanceled()) return;
		radius = (int)gd.getNextNumber();
		//doOval = (gd.getNextChoiceIndex()==1);
		//doSmooth = gd.getNextBoolean();
		prefs.put(Integer.class, "RSOM-den3D-radius", radius);
		//prefs.put(Boolean.class, "RSOM-den3D-doOval", doOval);
		//prefs.put(Boolean.class, "RSOM-den3D-doSmooth", doSmooth);

		// get input image (3D);
		sourceImage = IJ.getImage();
		if (!sourceImage.isStack()) return; // we don't compute non-stack
		int sourceWidth = sourceImage.getWidth();
		int sourceHeight = sourceImage.getHeight();
		int sourceDepth = sourceImage.getNSlices();
		// create empty density stack to fill later
		ImageStack densityStack = ImageStack.create(sourceWidth, sourceHeight, sourceDepth, 32);

		// compute kernel size, buffer size, and max pixel value sum in kernel
		kernelSize = 2*radius + 1;
		bufferWidth = sourceImage.getWidth() + kernelSize - 1;
		bufferHeight = sourceImage.getHeight() + kernelSize - 1;
		T = 255 * (float)(Math.pow(kernelSize, 3));

		// get padded stack in all x, y, and z (zero pad with radius in all 6 faces)
		ImageStack stackPadded = zeroPad3D(sourceImage, radius);

		// we timing the duration of the main computation
		double start = System.currentTimeMillis(); 
		getDensity(stackPadded, densityStack, radius, doOval);
		double duration = System.currentTimeMillis() - start;
		System.out.println("script operation takes " + String.valueOf(duration/1000) + " second.");
		
		ImagePlus impDensity = new ImagePlus(
			sourceImage.getTitle() + " (density, radius=" + radius + " voxel)", 
			densityStack);
		
		// depracated: perform 3D median filtering if required

		//if (doSmooth) {
		//	double iter = 0;
		//	while (++iter<radius)
		//		IJ.run(impDensity, "Median 3D...", "x=["+1.0+"] y=["+1.0+"] z=["+1.0+"]");
		//}

		
		impDensity.copyScale(sourceImage);
		impDensity.setSlice(sourceImage.getCurrentSlice());
		StackStatistics stats = new StackStatistics(impDensity);
		impDensity.setDisplayRange(stats.min, stats.max);
		impDensity.show();

		// adjust display range, apply "Fire" LUT, display density stack
		IJ.run(impDensity, "Fire", "");
		IJ.run("Collect Garbage", "");
		System.gc();	
		*/
		
		// make use of scijava parameter persistence storage	
		DefaultPrefService prefs = new DefaultPrefService();
		type = prefs.getInt(Integer.class, "RSOM-HomofftConvolve-type", type);
		param = prefs.getInt(Integer.class, "RSOM-HomofftConvolve-param", param);
		shape = prefs.getInt(Integer.class, "RSOM-HomofftConvolve-shape", shape);
		size = prefs.getDouble(Double.class, "RSOM-HomofftConvolve-size", size);
		// generate dialog	
		GenericDialogPlus gd = new GenericDialogPlus("get FFT Convolved image");
		gd.addImageChoice("source image", "");
		gd.addChoice("type", mapTypes, mapTypes[type]);
		gd.addChoice("measure", heteroParams, heteroParams[param]);
		gd.addChoice("kernel shape", kernelShapes, kernelShapes[shape]);
		gd.addNumericField("kernel size", size, 1, 4, "pixel");
		gd.addCheckbox("display kernel", false);
		gd.showDialog();
		if (gd.wasCanceled()) return;
        try {
			activeImage = gd.getNextImage();
		} catch (ArrayIndexOutOfBoundsException e) {}
        if (activeImage==null || activeImage.getType()!=ImagePlus.GRAY8) return;
        type = gd.getNextChoiceIndex();
        param = gd.getNextChoiceIndex(); 
        shape = gd.getNextChoiceIndex();
        size = gd.getNextNumber();
        boolean showKernel = gd.getNextBoolean();
        // save parameter back to sci-java storage
        prefs.put(Integer.class, "RSOM-HomofftConvolve-type", type);
        prefs.put(Integer.class, "RSOM-HomofftConvolve-param", param);
        prefs.put(Integer.class, "RSOM-HomofftConvolve-shape", shape);
		prefs.put(Double.class, "RSOM-HomofftConvolve-size", size);
		
		
		double start = System.currentTimeMillis(); 
		
		int currentSlice = activeImage.getCurrentSlice();
		ImagePlus heteroImage = getHeteroImage(FFTConvolve.getDensityImage(activeImage, shape, size, showKernel), heteroParams[param], type);
		if (type==0) {
			IJ.run(heteroImage, "Invert", "stack");
		}
		
		StackStatistics stats = new StackStatistics(heteroImage);
		double stackMin = stats.min;
		double stackMax = stats.max;
		IJ.run(heteroImage, "Fire", "");
		heteroImage.show();
		heteroImage.setSlice(currentSlice);
		heteroImage.getProcessor().setMinAndMax(stackMin, stackMax);
		
		System.gc();
		double duration = System.currentTimeMillis() - start;
 		System.out.println("heterogeneous image computation from fft convolution takes " + String.valueOf(duration/1000) + " second.");
				
	}
	
	public ImagePlus getHeteroImage (ImagePlus impDensity, String heteroMeasure, int mapType) {

		int width = impDensity.getWidth();
		int height = impDensity.getHeight();
		int depth = impDensity.getStackSize();
		ImageStack heteroStack = new ImageStack(width, height, depth);

		for (int z=0; z<depth; z++) {
			float[][] densityArray = impDensity.getStack().getProcessor(z+1).getFloatArray();
			float[][] heteroArray = new float[width][height];
			for (int y=0; y<height; y++) {
				for (int x=0; x<width; x++) {
					float p = densityArray[x][y];	// density value, used as probability
					
					switch (heteroMeasure) {
					case "entropy":		// entropy of Bernoulli distribution
						if (p==0 || p==1) {
							heteroArray[x][y] = 0;
						} else {
							heteroArray[x][y] = (float) (-p*Math.log(p)/Math.log(2.0) - (1-p)*Math.log(1-p)/Math.log(2.0));
						}
						break;
					case "variance":	// variance of Bernoulli distribution
						heteroArray[x][y] = p-p*p;
						break;
					case "stdDev":
						heteroArray[x][y] = (float) Math.sqrt(p-p*p);
						break;
					case "relative stdDev":
						heteroArray[x][y] = p==0 ? 0 : (float) Math.sqrt((1-p)/p);
						break;
					case "range":
						heteroArray[x][y] = p>0 ? 1 : 0;
						break;
					case "skewness":
						heteroArray[x][y] = p==0 ? 0 : (float) ((1-2*p)/Math.sqrt(p-p*p));
						break;
					}
				}
			}
			heteroStack.setProcessor( new FloatProcessor(heteroArray), z+1);	
		}
		
		String title = activeImage.getTitle() + (type==0 ? " homogeneous map (" : " heterogeneous map (") + heteroMeasure + ")";
		return new ImagePlus(title, heteroStack);
	}


}