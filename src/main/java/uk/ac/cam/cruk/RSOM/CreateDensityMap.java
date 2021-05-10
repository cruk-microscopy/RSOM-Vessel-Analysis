package uk.ac.cam.cruk.RSOM;


import org.scijava.prefs.DefaultPrefService;

import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.Prefs;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;
import ij.process.FloatProcessor;
import ij.process.ShortProcessor;
import ij.process.StackStatistics;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.ImageStatistics;
import ij.gui.GenericDialog;
import ij.gui.OvalRoi;
import ij.gui.Roi;
import ij.plugin.CanvasResizer;
import ij.plugin.ZProjector;
import ij.plugin.PlugIn;

import ij.util.ThreadUtil;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;


public class CreateDensityMap implements PlugIn {

	/*
	 * 
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
	 *  Author: Ziqiang Huang <Ziqiang.Huang@cruk.cam.ac.uk>	
	 *  Date: 2019.07.27
	 *  Version: 1.0
	 *  Known issues:
	 *  	- float precision returns some small negative values (in the range of 10E-6)
	 */
	
	/*  Multi-threaded version of sliding buffer algorithm for density map computation:
	 *  Input should be 8-bit gray or binary image stack;
	 *  This script partition the stack evenly into number of available logical
	 *  processors of the current computer. For each partition, it create a new 
	 *  thread to compute the density map of that sub-stack. When all finished,
	 *  it joins the result into a final result density map and displays it.
	 *  
	 *  It use sliding buffer algorithm for the density computation, which takes
	 *  O(N) for any given kernel size. For the computation (in each thread), it
	 *  create 3 buffers:
	 *  	a volume buffer, a 2D float array, that moves along Z,
	 *  	a box buffer, a 1D float array, that moves along Y,
	 *  	a column buffer, a single float value, that moves along X
	 *  	
	 *  	The volume buffer get the sum of kernel size slices along Z into a 2D array,
	 *  		with size equals to [width][height] of the input image stack
	 * 		The box buffer get the sum of kernel size rows along Y into a 1D array,
	 *  		with size equals to [width] of the input image stack
	 *  	The column buffer get the sum kernel size columns along X into a single float value
	 *  	
	 *  	Each voxel in the input padded stack get visited 1 time during the whole procedure.
	 */

	protected ImagePlus sourceImage;
	protected int radius;
	protected String[] kernelShapes = {"cube", "ball"};
	//protected String[] kernelShape = {"cube"};
	protected int shape = 0;
	protected double size = 20;
	
	protected boolean doOval = false;
	protected boolean doSmooth = false;

	protected int kernelSize; // size (diameter) of the kernel
	protected float T;	// total pixel count inside the 3D kernel
	protected int bufferWidth;	//the same as padded width
	protected int bufferHeight;	//the same as padded height
	
	@Override
	public void run(String arg) {
		
		// make use of scijava parameter persistence storage	
		DefaultPrefService prefs = new DefaultPrefService();
		shape = prefs.getInt(Integer.class, "RSOM-DenfftConvolve-shape", shape);
		size = prefs.getDouble(Double.class, "RSOM-DenfftConvolve-size", size);
		// generate dialog	
		GenericDialogPlus gd = new GenericDialogPlus("get FFT Convolved image");
		gd.addImageChoice("source image", "");
		gd.addChoice("kernel shape", kernelShapes, kernelShapes[shape]);
		gd.addNumericField("kernel size", size, 1, 4, "pixel");
		gd.addCheckbox("display kernel", false);
		gd.showDialog();
		if (gd.wasCanceled()) return;
        ImagePlus activeImage = null;
        try {
			activeImage = gd.getNextImage();
		} catch (ArrayIndexOutOfBoundsException e) {}
        if (activeImage==null || activeImage.getType()!=ImagePlus.GRAY8) return;
        int currentSlice = activeImage.getCurrentSlice();
        shape = gd.getNextChoiceIndex();
        size = gd.getNextNumber();
        boolean showKernel = gd.getNextBoolean();
        // save parameter back to sci-java storage
        prefs.put(Integer.class, "RSOM-DenfftConvolve-shape", shape);
		prefs.put(Double.class, "RSOM-DenfftConvolve-size", size);
		
		ImagePlus densityImage = FFTConvolve.getDensityImage(activeImage, shape, size, showKernel);
		
		// prepare image for display
		StackStatistics stats = new StackStatistics(densityImage);
		double stackMin = stats.min;
		double stackMax = stats.max;
		String title = activeImage.getTitle() + " density map";
		densityImage.setTitle(title);
		IJ.run(densityImage, "Fire", "");
		densityImage.show();
		densityImage.setSlice(currentSlice);
		densityImage.getProcessor().setMinAndMax(stackMin, stackMax);
		
		System.gc();
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
	}

	// initialize volume buffer with padded stack
	public void initializeVolumeBuffer (
			float[][] volumeBuffer,
			ImageStack paddedStack, // padded stack
			int initSlice	// one based, has to be no larger than original stack depth
			) { 
		//Arrays.fill(volumeBuffer, 0.0);	
		int padWidth = paddedStack.getWidth();
		int padHeight = paddedStack.getHeight();
		if ((padWidth-sourceImage.getWidth()) != (radius*2) ||
				(padHeight-sourceImage.getHeight()) != (radius*2)) {
			IJ.log("mismatch size of padded stack!");
			return;
		}
		int oriZ = paddedStack.getSize()-kernelSize+1;
		if (initSlice>oriZ) {
			IJ.log("initialize slice outside original stack!");
			return;
		}
		ImagePlus initBufferImp = ZProjector.run(new ImagePlus("padded stack", paddedStack), "sum", initSlice , initSlice+kernelSize-1);
		float[][] floatArray = initBufferImp.getProcessor().getFloatArray();
		for (int w=0; w<padWidth; w++) {
			for (int h=0; h<padHeight; h++) {
				//volumeBuffer[h][w] = Float.intBitsToFloat(intArray[w][h]);
				volumeBuffer[h][w] = floatArray[w][h];
			}
		}
		initBufferImp.changes=false;
		initBufferImp.close();
		System.gc();
	}
	// update volume buffer, add new slice, remove old slice
	public void updateVolumeBuffer(
			float[][] volumeBuffer,
			ImageProcessor oldSlice,
			ImageProcessor newSlice
			) {
		if (oldSlice.getWidth()!=bufferWidth || 
				oldSlice.getHeight()!=bufferHeight ||
				newSlice.getWidth()!=bufferWidth ||
				newSlice.getHeight()!=bufferHeight) {
			IJ.log("mismatch size of slice and buffer!");
			return;
		}
		float[][] oldArray = oldSlice.getFloatArray();
		float[][] newArray = newSlice.getFloatArray();
        for (int i=0; i<bufferWidth; i++) {
        	for (int j=0; j<bufferHeight; j++) {
        		volumeBuffer[j][i] += (newArray[i][j]-oldArray[i][j]);
        	}
        }
    }
    // initialize box buffer with current volume buffer
	public void initializeBoxBuffer(
			float[] boxBuffer,
			float[][] volumeBuffer
			) {
		Arrays.fill(boxBuffer, 0.0f);
		for (int j=0; j<kernelSize; j++) {
			for (int i=0; i<bufferWidth; i++) {
				boxBuffer[i] += volumeBuffer[j][i];
			}
		}
	}
	// update box buffer, add new row, remove old row
	public void updateBoxBuffer(
			float[] boxBuffer,
			float[] oldRow, 
			float[] newRow
			) {
		if (oldRow.length!=bufferWidth || newRow.length!=bufferWidth)	{
			IJ.log("mismatch size of row and buffer!");
			return;
		}
        for (int i=0; i<bufferWidth; i++) {
        	boxBuffer[i] += (newRow[i]-oldRow[i]);
        }
    }
    
	/* 
	 *  Compute density stack for a range of slices in padded stack
	 *  designed for multi-thread operation:
	 *  	each time a thread calling this function, 
	 *  	create a new volume and box buffer.
	 * 		initialize volume buffer, with the start slice, for each sub-stack,
	 * 		initialize box buffer, with volume buffer, for each new slice,
	 * 		initilaize column buffer (bufferColumn), with box buffer, for each new row
	 */
    public void getDensityPartition ( 
	    	ImageStack paddedStack,
	    	ImageStack resultStack,
	    	int radius,
	    	boolean ovalRoi,
	    	int startSlice,
	    	int endSlice
	    	) {
    	int resultWidth = resultStack.getWidth(); 
		int resultHeight = resultStack.getHeight();
		int resultDepth = resultStack.getSize();
		// end slice can not exceed result stack size
		endSlice = Math.min(endSlice, resultDepth);
				
		// create local buffer variables
		// we use a 2D float array to store slice result
		// this is ~30% faster than image processor operation
		float[][] sliceFloatArray = new float[resultWidth][resultHeight];
		float[][] bufferVolume = new float[bufferHeight][bufferWidth];
		float[] bufferBox = new float[bufferWidth];
		float bufferColumn = 0;

		// iterate through slices in sub-stack
		for (int d=startSlice; d<=endSlice; d++) { // move volume along z
			// initialize volume buffer for start slice, get the sum of the [1, kernel] slice into bufferVolume
			if (d==startSlice)
				initializeVolumeBuffer(bufferVolume, paddedStack, d);
			else 
				updateVolumeBuffer(bufferVolume, paddedStack.getProcessor(d-1), paddedStack.getProcessor(d+kernelSize-1));
			// iterate through rows in slice
			for (int h=0; h<resultHeight; h++) { // move box along y
				// initialize box buffer for each new slice, get the sum of the [1, kernel] row into bufferBox
				if (h==0)
					initializeBoxBuffer(bufferBox, bufferVolume);
				else
					updateBoxBuffer(bufferBox, bufferVolume[h-1], bufferVolume[h+kernelSize-1]); // SWAP 1st and 2nd dimension for array indexing
				
				// iterate through columns in row
				for (int w=0; w<resultWidth; w++) { // move column along x
					// initialize column buffer for each new row, get the sum of [1, kernel] column into bufferColumn
					if (w==0) {
						bufferColumn=0.0f;
						for (int i=0; i<kernelSize; i++)
							bufferColumn += bufferBox[i];
					} else {
						bufferColumn += (bufferBox[w+kernelSize-1] - bufferBox[w-1]);
					}
					/* debug small negative values from float operation
					if (bufferColumn<0)
						debugValue = Math.min(debugValue, bufferColumn/T);
					sliceFloatArray[w][h] =  (float)(Math.max(bufferColumn/T, 0.0));
					*/
					sliceFloatArray[w][h] =  bufferColumn/T;
				}
			}
			// use the result 2D float array to fill each slice in sub-stack
			resultStack.setProcessor(new FloatProcessor(sliceFloatArray),d);
		}
    }

	/* 
	 *  Compute density stack in padded stack
	 *  designed for multi-thread operation:
	 *  	partition the padded stack evenly into number of logical CPUs, 
	 *  	for each CPU, pass a sub-stack of the padded stack into getDensityPartition function,
	 * 		compute the density map for the sub-stack,
	 * 		and fill the value back into the global result density stack.
	 */
    public void getDensity ( // 3D, concurrency version
	    	ImageStack paddedStack,
	    	ImageStack densityStack,
	    	int radius,
	    	boolean ovalRoi
	    	) {
		int resultDepth = paddedStack.getSize() - (2*radius);
		/* debug number of thread
		getDensityPartition(paddedStack, densityStack, radius, ovalRoi, 1, resultDepth);
		*/	
		final AtomicInteger ai = new AtomicInteger(0);
		final int n_cpus = Prefs.getThreads();
		final int dec = (int) Math.ceil((double) resultDepth / (double) n_cpus);
		Thread[] threads = ThreadUtil.createThreadArray(n_cpus);
		for (int ithread=0; ithread<threads.length; ithread++) {
			threads[ithread] = new Thread() {
				public void run() {
					for (int k=ai.getAndIncrement(); k<n_cpus; k=ai.getAndIncrement()) {
						getDensityPartition(paddedStack, densityStack, radius, ovalRoi, dec*k+1, dec*(k+1));
					}
				}
			};
		}
		ThreadUtil.startAndJoin(threads);
    }

	// zero padding the stack in all 6 faces with size=radius of the kernel
	public ImageStack zeroPad3D ( // included 2D case (we don't pad 1 slice)
			ImagePlus imp,	// 3D input image (8-bit, binary)
			int padSize
			) {
		ImageProcessor ip = imp.getProcessor();	
		if (!imp.isStack()) {
			ImageProcessor paddedSlice = new CanvasResizer().expandImage(ip, (ip.getWidth()+padSize*2), (ip.getHeight()+padSize*2), padSize, padSize);
			ImageStack paddedStack = new ImageStack(paddedSlice.getWidth(), paddedSlice.getHeight());
			paddedStack.addSlice(paddedSlice);
			return	paddedStack;
		}
		int oldw = imp.getWidth();		int oldh = imp.getHeight();
		int neww = oldw + padSize*2;	int newh = oldh + padSize*2;
		byte[] emptyPixels = new byte[neww*newh];
		
		ImageStack stack = imp.getStack();
		ImageStack paddedStack = new ImageStack(neww, newh);

		for (int z=0; z<padSize; z++) { // add pre-z empty (black) slices
			paddedStack.addSlice(new ByteProcessor(neww, newh, emptyPixels, null));
		}
		for (int z=0; z<stack.getSize(); z++) {
			ImageProcessor paddedSlice = new CanvasResizer().expandImage(stack.getProcessor(z+1), neww, newh, padSize, padSize);
			paddedStack.addSlice(paddedSlice);
		}
		for (int z=0; z<padSize; z++) { // add post-z empty (black) slices
			paddedStack.addSlice(new ByteProcessor(neww, newh, emptyPixels, null));
		}
		return paddedStack;
	}

}
