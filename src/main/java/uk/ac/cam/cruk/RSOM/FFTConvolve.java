package uk.ac.cam.cruk.RSOM;

import java.util.Arrays;

import org.scijava.prefs.DefaultPrefService;

import fiji.util.gui.GenericDialogPlus;
import ij.IJ; 
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.NewImage;
import ij.plugin.CanvasResizer;
import ij.plugin.Duplicator;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import net.imglib2.img.ImagePlusAdapter;

import io.scif.img.ImgIOException;
import io.scif.img.ImgOpener;
import net.imglib2.algorithm.fft2.FFTConvolution;
import net.imglib2.exception.IncompatibleTypeException;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.logic.BoolType;
import net.imglib2.type.numeric.integer.ByteType;
import net.imglib2.util.RealSum;


public class FFTConvolve {
	
	protected final static String[] kernelShapes = {"cube", "ball"};
	protected static int shape = 0;
	protected static double size = 20;
	protected static boolean showKernel = false;
	
	//protected static double kernelVolume; 	// total voxel count contained within the kernel
	protected static double minDensity;	// minimum density above zero
	protected static final double maxDensity = 1;
	
public static ImagePlus getDensityImage(
		ImagePlus inputImage,
		int shape,
		double size,
		boolean showKernel
		) {
     	double start = System.currentTimeMillis(); 		
     	int sizeInt = (int)Math.ceil(size);
        ImagePlus impSource = inputImage.duplicate();
        addBorder(impSource, sizeInt);
        int numC = impSource.getNChannels();
        int numZ = impSource.getNSlices();
        int numT = impSource.getNFrames();

        ImagePlus impKernel = getKernel(shape, size);
        if (impKernel==null) return null;
        
        final Img< FloatType > image =  ImagePlusAdapter.convertFloat( impSource );
        final Img< FloatType > kernel = ImagePlusAdapter.convertFloat( impKernel );
        norm( kernel );
        
        new FFTConvolution<>( image, kernel ).convolve();
        ImagePlus virtualStack = ImageJFunctions.wrapFloat(image, "density map");
        virtualStack.setDimensions(numC, numZ, numT);

        ImagePlus impResult = new Duplicator().run(virtualStack, 1, numC, 1, numZ, 1, numT);
        removeBorder(impResult, sizeInt);
        trimImage(impResult);
 		virtualStack.close();
 		impSource.changes=false; impSource.close();
 		
 		if (showKernel) {
 			String kernelName = "kernel (" + kernelShapes[shape] + ", " + String.valueOf(size) + " pixel)";
 			addBorder(impKernel, 1);
 			impKernel.setTitle(kernelName);
 			impKernel.show();
 			impKernel.setSlice((int)((size+1)/2));
 			//IJ.run(impKernel, "Extend Image Borders", "left=1 right=1 top=1 bottom=1 front=1 back=1 fill=Black");
 			//ImagePlus extKernel = WindowManager.getImage("kernel-ext");
 			//extKernel.setTitle("kernel (" + kernelShapes[shape] + "," + String.valueOf(size) + ")");	
 		} else {
 			impKernel.changes=false; impKernel.close();
 		}
 		System.gc();
 		double duration = System.currentTimeMillis() - start;
 		System.out.println("density image computation from fft convolution takes " + String.valueOf(duration/1000) + " second.");
 		
 		return impResult;
 		//impResult.show();
	}



	public static ImagePlus getDensityImage() {
		
		// make use of scijava parameter persistence storage	
		DefaultPrefService prefs = new DefaultPrefService();
		shape = prefs.getInt(Integer.class, "RSOM-fftConvolve-shape", shape);
		size = prefs.getDouble(Double.class, "RSOM-fftConvolve-size", size);
		showKernel = prefs.getBoolean(Boolean.class, "RSOM-fftConvolve-showKernel", showKernel);
		// generate dialog	
		GenericDialogPlus gd = new GenericDialogPlus("get FFT Convolved image");
		gd.addImageChoice("source image", "");
		gd.addChoice("kernel shape", kernelShapes, kernelShapes[shape]);
		gd.addNumericField("kernel size", size, 1, 4, "pixel");
		gd.addCheckbox("display kernel", showKernel);
		gd.showDialog();
		if (gd.wasCanceled()) return null;
        ImagePlus activeImage = null;
        try {
			activeImage = gd.getNextImage();
		} catch (ArrayIndexOutOfBoundsException e) {}
        if (activeImage==null || activeImage.getType()!=ImagePlus.GRAY8) return null;
        shape = gd.getNextChoiceIndex();
        size = gd.getNextNumber();
        // save parameter back to sci-java storage
        prefs.put(Integer.class, "RSOM-fftConvolve-shape", shape);
		prefs.put(Double.class, "RSOM-fftConvolve-size", size);
		prefs.put(Boolean.class, "RSOM-fftConvolve-showKernel", showKernel);
        
		
     	double start = System.currentTimeMillis(); 		
       
        ImagePlus impSource = activeImage.duplicate();
        int numC = impSource.getNChannels();
        int numZ = impSource.getNSlices();
        int numT = impSource.getNFrames();

        ImagePlus impKernel = getKernel(shape, size);
        if (impKernel==null) return null;
        
        final Img< FloatType > image =  ImagePlusAdapter.convertFloat( impSource );
        final Img< FloatType > kernel = ImagePlusAdapter.convertFloat( impKernel );
        //norm( kernel );
        
        new FFTConvolution<>( image, kernel ).convolve();
        ImagePlus virtualStack = ImageJFunctions.wrapFloat(image, "density map");
        virtualStack.setDimensions(numC, numZ, numT);

        ImagePlus impResult = new Duplicator().run(virtualStack, 1, numC, 1, numZ, 1, numT);
 		virtualStack.close();
 		impSource.changes=false; impSource.close();
 		impKernel.changes=false; impKernel.close();
 		System.gc();
 		double duration = System.currentTimeMillis() - start;
 		IJ.log("script operation takes " + String.valueOf(duration/1000) + " second.");
 		
 		return impResult;
 		//impResult.show();
	}
	 /*
     * Computes the sum of all pixels in an iterable using RealSum
     *
     * @param iterable - the image data
     * @return - the sum of values
     */
    public static <T extends RealType<T>>double sumImage(
    		final Iterable<T> iterable
    		) {
        final RealSum sum = new RealSum();
        for (final T type : iterable )
            sum.add( type.getRealDouble() );
        return sum.getSum();
    }
	
    /*
     * Norms all image values so that their sum is 1
     *
     * @param iterable - the image data
     */
    public static void norm(
    		final Iterable<FloatType> iterable
    		) {
        final double sum = sumImage( iterable );
        minDensity = 1/sum;
        for ( final FloatType type : iterable )
            type.setReal(type.get()/sum/255); //type.setReal( type.get() /  sum/255 );
    }
 
    /*
     * Create kernel image that resemble the shape of the kernel as white pixels in black background
     * 
     * @param shape - kernel shape
     * @param size - kernel size
     */
    public static ImagePlus getKernel(
    		int shape,
    		double size
    		) {
    	if (size==0) return null;
    	int[][] kernel = null;
    	switch (shape) {
    	case 0:	// cube kernel
    		kernel = getCubeKernel(size);
    		break;
    	case 1:	// ball kernel
    		kernel = getBallKernel(size);
    		break;
    	}
    	return getKernel(kernel);
    }
    
    public static ImagePlus getKernel(int[][] kernel) {
		int kernelSize = kernel.length;
		ImagePlus imp = NewImage.createByteImage(
			"kernel r="+String.valueOf(kernelSize), 
			kernelSize, kernelSize, kernelSize, NewImage.FILL_BLACK);
		for (int z=0; z<kernelSize; z++) {
			imp.setSlice(z+1);
			int[][] intArray = new int[kernelSize][kernelSize];
			for (int y=0; y<kernelSize; y++) {
				for (int x=0; x<kernelSize; x++) {
					intArray[x][y] = 255*kernel[z][y*kernelSize + x];
				}
			}
			imp.getProcessor().setIntArray(intArray);
		}
		imp.setTitle("kernel");
		return imp;
	}
    /*
     * Create ball shape kernel with diameter D
     * 
     * @param D - diameter of the ball
     * @return - [z][x*y] to store a binary 3D pixel array for the kernel image, centered in the bounding box of volume Diam^3
     */
    public static int[][] getBallKernel (double D) {
    	int Dint = (int)Math.ceil(D);	// ceil integer of diameter, to construct the bounding box
    	double R = D/2;		// get radius
    	int Rint = (int)Math.ceil(R);	// ceil integer of radius
		double R2 = R*R;
		
		int[][] kernel = new int[Dint][Dint * Dint];	// initialize kernel array
		double center = (Dint-1)/2;	// get center coordinate
	
		for (int z=0; z<Rint; z++) {
			for (int y=0; y<Rint; y++) {
				for (int x=0; x<Rint; x++) {
					//for a given voxel[x,y,z], its distance to center will be [(x-center)^2 + (y-center)^2 + (z-center)^2]
					double distance = Math.pow(x-center,2) +  Math.pow(y-center,2) +  Math.pow(z-center,2);		
					if (distance<R2) {	// distance shorter than radius, pixel belongs to kernel
						kernel[z][Dint*y + x] = 1;							// 1st quandrant
						kernel[z][Dint*y + (Dint-1-x)] = 1;					// 2nd quandrant
						kernel[z][Dint*(Dint-1-y) + x] = 1;					// 3rd quandrant
						kernel[z][Dint*(Dint-1-y) + (Dint-1-x)] = 1;		// 4th quandrant
						kernel[Dint-1-z][Dint*y + x] = 1;					// 5th quandrant
						kernel[Dint-1-z][Dint*y + (Dint-1-x)] = 1;			// 6th quandrant
						kernel[Dint-1-z][Dint*(Dint-1-y) + x] = 1;			// 7th quandrant
						kernel[Dint-1-z][Dint*(Dint-1-y) + (Dint-1-x)] = 1;	// 8th quandrant
					}
				}
			}
		}
		return kernel;
	}
    
    /*
     * Create cube shape kernel with diameter(size) D
     * 
     * @param D - side size of the cube
     * @return - [z][x*y] to store a binary 3D pixel array for the kernel image, centered in the bounding box of volume Diam^3
     */
    public static int[][] getCubeKernel (double D) {
    	int Dint = (int)Math.ceil(D);	// ceil integer of size, to construct the bounding box
		int[][] kernel = new int[Dint][Dint * Dint];	// initialize kernel array
		int[] slice = new int[Dint * Dint];
		/*
		for (int i=1; i<Dint-1; i++) {
			for (int j=1; j<Dint-1; j++) {
				slice[Dint*i + j] = 1;
			}
		}
		for (int k=1; k<Dint; k++) {
			kernel[k] = slice;
		}*/
		Arrays.fill(slice,1);
		Arrays.fill(kernel, slice);
		return kernel;
	}

    public static void displayKernel(int[][] kernel) {
		int kernelSize = kernel.length;
		ImagePlus imp = NewImage.createByteImage(
			"kernel r="+String.valueOf(kernelSize), 
			kernelSize, kernelSize, kernelSize, NewImage.FILL_BLACK);
		for (int z=0; z<kernelSize; z++) {
			imp.setSlice(z+1);
			int[][] intArray = new int[kernelSize][kernelSize];
			for (int y=0; y<kernelSize; y++) {
				for (int x=0; x<kernelSize; x++) {
					intArray[x][y] = 255*kernel[z][y*kernelSize + x];
				}
			}
			imp.getProcessor().setIntArray(intArray);
		}
		imp.show();
	}
    
    // trim image resulted from fft convolve
    public static void trimImage (ImagePlus imp) {
    	int width = imp.getWidth();
    	int height = imp.getHeight();
    	int depth = imp.getNSlices();
    
    	for (int z=0; z<depth; z++) {
    		float[][] densityArray = imp.getStack().getProcessor(z+1).getFloatArray();
    		for (int y=0; y<height; y++) {
    			for (int x=0; x<width; x++) {
    				if (densityArray[x][y]==0) continue;
    				if (densityArray[x][y]<minDensity) {
    					densityArray[x][y] = 0;
    				} else if (densityArray[x][y]>maxDensity) {
    					densityArray[x][y] = 1;
    				} else {	// currently don't treat values in the acceptable range (in the future, consider check decimal place)
    					continue;
    				}
    			}
    		}
    		imp.getStack().setProcessor(new FloatProcessor(densityArray), z+1);	
    	}
    }
    
    // zero padding the stack in all 6 faces with borderSize
 	public static void addBorder ( // included 2D case (we don't pad 1 slice)
 			ImagePlus imp,	// 3D input image (8-bit, binary)
 			int borderSize
 			) {
 		ImageProcessor ip = imp.getProcessor();	
 		if (!imp.isStack()) {
 			ImageProcessor paddedSlice = new CanvasResizer().expandImage(ip, (ip.getWidth()+borderSize*2), (ip.getHeight()+borderSize*2), borderSize, borderSize);
 			ImageStack paddedStack = new ImageStack(paddedSlice.getWidth(), paddedSlice.getHeight());
 			paddedStack.addSlice(paddedSlice);
 			imp.setStack(paddedStack);
 			return;
 		}
 		int oldw = imp.getWidth();		int oldh = imp.getHeight();
 		int neww = oldw + borderSize*2;	int newh = oldh + borderSize*2;
 		byte[] emptyPixels = new byte[neww*newh];
 		
 		ImageStack stack = imp.getStack();
 		ImageStack paddedStack = new ImageStack(neww, newh);

 		for (int z=0; z<borderSize; z++) { // add pre-z empty (black) slices
 			paddedStack.addSlice(new ByteProcessor(neww, newh, emptyPixels, null));
 		}
 		for (int z=0; z<stack.getSize(); z++) {
 			ImageProcessor paddedSlice = new CanvasResizer().expandImage(stack.getProcessor(z+1), neww, newh, borderSize, borderSize);
 			paddedStack.addSlice(paddedSlice);
 		}
 		for (int z=0; z<borderSize; z++) { // add post-z empty (black) slices
 			paddedStack.addSlice(new ByteProcessor(neww, newh, emptyPixels, null));
 		}
 		imp.setStack(paddedStack);
 		return;
 	}
 	
    // remove borderSize from all 6 faces from the input image
 	public static void removeBorder ( // included 2D case (we don't pad 1 slice)
 			ImagePlus imp,	// 3D input image (8-bit, binary)
 			int borderSize
 			) {
 		int numZ = imp.getNSlices();
 		int oldw = imp.getWidth();		int oldh = imp.getHeight();
 		int neww = oldw - borderSize*2;	int newh = oldh - borderSize*2;
 		ImageStack stack = imp.getStack();
 		ImageStack trimmedStack = new ImageStack(neww, newh);
 		if (numZ<=2*borderSize) {	// not enough slice to subtract
 			for (int z=0; z<imp.getNSlices(); z++) {
 				ImageProcessor trimmedSlice = new CanvasResizer().expandImage(stack.getProcessor(z+1), neww, newh, -borderSize, -borderSize);
 	 			trimmedStack.addSlice(trimmedSlice);
 			}
 		} else {
 			for (int z=borderSize; z<stack.getSize()-borderSize; z++) {
 	 			ImageProcessor trimmedSlice = new CanvasResizer().expandImage(stack.getProcessor(z+1), neww, newh, -borderSize, -borderSize);
 	 			trimmedStack.addSlice(trimmedSlice);
 	 		}
 		}
 		imp.setStack(trimmedStack);
 		return;
 	}
 	
 	
    public static void main( final String[] args ) throws ImgIOException, IncompatibleTypeException {
        // open an ImageJ window
        //new ImageJ();
 
        // run the example
       
		
		
    }
    
}
