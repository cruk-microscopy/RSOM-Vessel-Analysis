package uk.ac.cam.cruk.RSOM;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;

import org.scijava.prefs.DefaultPrefService;


//import features.TubenessProcessor;
import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Roi;
import ij.io.FileSaver;
import ij.plugin.Filters3D;
import ij.plugin.PlugIn;
import ij.plugin.ZProjector;
import ij.plugin.frame.RoiManager;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.StackConverter;
import ij.process.StackStatistics;

public class VesselnessFilter2 implements PlugIn {

	//protected boolean loadImage = false;
	protected String inputDirPath = "";
	
	protected boolean doPreprocess = false;
	protected double bgRadius = 5.0;	// 5.0 pixel as default background subtraction radius
	protected double mfRadius = 2.5;	// 2.5 pixel as default median filter (3D) radius
	
	//protected boolean doRoi = false;
	//protected String roiPath = "";
	
	protected double diamMax = 200.0; // 5 pixel as maximum kernel radius;
	protected double diamMin = 40.0; // 1 pixel as minimum kernel radius;
	protected double diamStep = 40.0; // 1 pixel increment each time;
	
	protected int method = 1;
	protected double frangi_a = 0.5;
	protected double frangi_b = 0.5;
	protected double frangi_c = 0.5;
	protected double sato_alpha = 1.0;
	
	protected double pixelSize = 20.0;
	protected boolean convertToGray8 = true;
	protected String saveDirPath = "";
	protected boolean saveScaleImage = false;
	protected boolean saveSelectionImage = false;
	protected boolean saveProjection = true;
	
	@Override
	public void run(String arg0) {

		// make use of scijava parameter persistence storage	
		DefaultPrefService prefs = new DefaultPrefService();
		//loadImage = prefs.getBoolean(Boolean.class, "RSOM-VF-loadImage", loadImage);
		inputDirPath = prefs.get(String.class, "RSOM-VF-batch-inputDirPath", inputDirPath);
		
		doPreprocess = prefs.getBoolean(Boolean.class, "RSOM-VF-batch-doPreprocess", doPreprocess);
		bgRadius = prefs.getDouble(Double.class, "RSOM-VF-batch-bgRadius", bgRadius);
		mfRadius = prefs.getDouble(Double.class, "RSOM-VF-batch-mfRadius", mfRadius);
		
		//doRoi = prefs.getBoolean(Boolean.class, "RSOM-VF-doRoi", doRoi);
		//roiPath = prefs.get(String.class, "RSOM-VF-roiPath", roiPath);
		
		diamMax = prefs.getDouble(Double.class, "RSOM-VF-batch-diamMax", diamMax);
		diamMin = prefs.getDouble(Double.class, "RSOM-VF-batch-diamMin", diamMin);
		diamStep = prefs.getDouble(Double.class, "RSOM-VF-batch-diamStep", diamStep);
		
		method = prefs.getInt(Integer.class, "RSOM-VF-batch-method", method);
		frangi_a = prefs.getDouble(Double.class, "RSOM-VF-batch-frangi_a", frangi_a);
		frangi_b = prefs.getDouble(Double.class, "RSOM-VF-batch-frangi_b", frangi_b);
		frangi_c = prefs.getDouble(Double.class, "RSOM-VF-batch-frangi_c", frangi_c);
		sato_alpha = prefs.getDouble(Double.class, "RSOM-VF-batch-sato_alpha", sato_alpha);
		
		convertToGray8 = prefs.getBoolean(Boolean.class, "RSOM-VF-batch-convertToGray8", convertToGray8);
		
		saveDirPath = prefs.get(String.class, "RSOM-VF-batch-saveDirPath", saveDirPath);
		saveScaleImage = prefs.getBoolean(Boolean.class, "RSOM-VF-batch-saveScaleImage", saveScaleImage);
		saveSelectionImage = prefs.getBoolean(Boolean.class, "RSOM-VF-batch-saveSelectionImage", saveSelectionImage);
		saveProjection = prefs.getBoolean(Boolean.class, "RSOM-VF-batch-saveProjection", saveProjection);
		// generate dialog	
		GenericDialogPlus gd = new GenericDialogPlus("Vesselness filtering (batch mode)");
		
		gd.addDirectoryField("input folder", inputDirPath);
		
		gd.addCheckbox("do pre-processing", doPreprocess);
		gd.addNumericField("background radius", bgRadius, 1, 3, "pixel");
		gd.addNumericField("median filter radius", mfRadius, 1, 3, "pixel");
		
		gd.addCheckbox("force isotropy", true);
		gd.addNumericField("vessel diameter max", diamMax, 1, 3, "µm");
		gd.addNumericField("vessel diameter min", diamMin, 1, 3, "µm");
		gd.addNumericField("vessel diameter step", diamStep, 1, 3, "µm");
		
		gd.addChoice("method", VesselnessProcessor.methodNames, VesselnessProcessor.methodNames[method]);
		gd.addNumericField("Frangi a", frangi_a, 2);
		gd.addNumericField("Frangi b", frangi_b, 2);
		gd.addNumericField("Frangi c", frangi_c, 2);
		gd.addNumericField("Sato alpha", sato_alpha, 2);
		
		gd.addCheckbox("convert to 8-bit", convertToGray8);
		
		gd.addDirectoryField("save to folder", saveDirPath);
		gd.addCheckbox("save image at each scale", saveScaleImage);
		gd.addCheckbox("save scale selection", saveSelectionImage);
		gd.addCheckbox("save 2D max projection image", saveProjection);
		
		gd.showDialog();
		if (gd.wasCanceled()) return;
		ImagePlus activeImage = null;
		
		inputDirPath = gd.getNextString();
		
		doPreprocess = gd.getNextBoolean();
		bgRadius = gd.getNextNumber();
		mfRadius = gd.getNextNumber();
		
		boolean forceIsotropy = gd.getNextBoolean();
		
		diamMax = gd.getNextNumber();
		diamMin = gd.getNextNumber();
		diamStep = gd.getNextNumber();
		
		method = gd.getNextChoiceIndex();
		frangi_a = gd.getNextNumber();
		frangi_b = gd.getNextNumber();
		frangi_c = gd.getNextNumber();
		sato_alpha = gd.getNextNumber();
		
		convertToGray8 = gd.getNextBoolean();
		
		saveDirPath = gd.getNextString();
		saveScaleImage = gd.getNextBoolean();
		saveSelectionImage = gd.getNextBoolean();
		saveProjection = gd.getNextBoolean();
		
		//ImagePlus impInput = loadImage ? IJ.openImage(inputDirPath) : activeImage;
		//if (impInput==null) return;
		
		/*
		if (calUnit.equals("micron") || calUnit.equals("um") || calUnit.equals("µm"))
			pixelSize = impInput.getCalibration().pixelWidth;
		else
			pixelSize = IJ.getNumber("pixel size (µm) of image: " + impInput.getTitle(), 20.0);
		*/
		//if (pixelSize==Integer.MIN_VALUE) return;
		// put parameter into scijava persistence storage
		
		prefs.put(String.class, "RSOM-VF-batch-inputDirPath", inputDirPath);
		
		prefs.put(Boolean.class, "RSOM-VF-batch-doPreprocess", doPreprocess);
		prefs.put(Double.class, "RSOM-VF-batch-bgRadius", bgRadius);
		prefs.put(Double.class, "RSOM-VF-batch-mfRadius", mfRadius);
		
		
		prefs.put(Double.class, "RSOM-VF-batch-diamMax", diamMax);
		prefs.put(Double.class, "RSOM-VF-batch-diamMin", diamMin);
		prefs.put(Double.class, "RSOM-VF-batch-diamStep", diamStep);
		
		prefs.put(Integer.class, "RSOM-VF-batch-method", method);
		prefs.put(Double.class, "RSOM-VF-batch-frangi_a", frangi_a);
		prefs.put(Double.class, "RSOM-VF-batch-frangi_b", frangi_b);
		prefs.put(Double.class, "RSOM-VF-batch-frangi_c", frangi_c);
		prefs.put(Double.class, "RSOM-VF-batch-sato_alpha", sato_alpha);
		
		prefs.put(Boolean.class, "RSOM-VF-batch-convertToGray8", convertToGray8);
		
		prefs.get(String.class, "RSOM-VF-batch-saveDirPath", saveDirPath);
		prefs.put(Boolean.class, "RSOM-VF-batch-saveScaleImage", saveScaleImage);
		prefs.put(Boolean.class, "RSOM-VF-batch-saveSelectionImage", saveSelectionImage);
		prefs.put(Boolean.class, "RSOM-VF-batch-saveProjection", saveProjection);
		
		double sigmaMax = diamMax/2;	// (2*pixelSize); // 5 pixel as maximum kernel radius;
		double sigmaMin = diamMin/2;	// (2*pixelSize); // 1 pixel as minimum kernel radius;
		double sigmaStep = diamStep/2;	// (2*pixelSize); // 1 pixel increment each time;
		
		//double start = System.currentTimeMillis();
		
		File inputDir = new File(inputDirPath);
		if (!inputDir.exists()) return;
		if (!saveDirPath.endsWith(File.separator)) saveDirPath += File.separator;
		
		File[] fileList = inputDir.listFiles( new FilenameFilter() {
	        @Override
	        public boolean accept(File f, String name) {
	            return name.endsWith(".tif");
	        }}
		);
		
		for (File file : fileList) {
			ImagePlus impInput = IJ.openImage(file.getAbsolutePath());
			
			if (!impInput.isStack() || impInput.getStackSize()==1) continue;
			
			String calUnit = impInput.getCalibration().getUnit().toLowerCase();
			pixelSize = impInput.getCalibration().pixelWidth;
			
			String title = impInput.getTitle();
			if (title.endsWith(".tif")) title = title.substring(0, title.length()-4);
			
			//impInput.show();
			ImagePlus original = impInput.duplicate();
			
			if (doPreprocess) {
				original.setCalibration(null); // make sure pre-processing is done in pixel unit
				IJ.run(original, "Subtract Background...", "rolling="+bgRadius+" stack");	
				ImageStack stack2 = Filters3D.filter(original.getStack(), Filters3D.MEDIAN, (float)mfRadius, (float)mfRadius, (float)mfRadius);
				original.setStack(stack2);
				original.setTitle(impInput.getTitle() + " preprocessed");
				//original.show();
			}
			
			original.setCalibration(impInput.getCalibration());
			if (!calUnit.contentEquals("micron")) {	// if not calibrated, caibrate to 20 micron / pixel
				original.getCalibration().setUnit("micron");
				original.getCalibration().pixelWidth = 20;
			}
			
			if (forceIsotropy) {
				original.getCalibration().pixelHeight = original.getCalibration().pixelWidth;
				original.getCalibration().pixelDepth = original.getCalibration().pixelWidth;
			}
			
			int width = original.getWidth();
			int height = original.getHeight();
			int depth = original.getStackSize();
			StackStatistics stat = new StackStatistics (impInput);
			double dataDynamicRange = stat.max - stat.min;
	
			ArrayList<ImagePlus> tubeImps = new ArrayList<ImagePlus>();
	
			for (double sigma=sigmaMin; sigma<=sigmaMax; sigma+=sigmaStep) {
				//System.out.println( "debug: sigma: " + sigma);
				IJ.showStatus("Vesselness filtering with sigma: " + String.valueOf(sigma) + " " + calUnit);
				//sigma = Math.round(sigma*100)/100;
				
				//TubenessProcessor tp = new TubenessProcessor(sigma, false);
				//ImagePlus result = tp.generateImage(original);
				
				VesselnessProcessor vp = new VesselnessProcessor(sigma, true);
				vp.setMethod(method);	
				vp.setNormFactor(pixelSize * pixelSize * dataDynamicRange * 0.3);
				if (method==0) {
					if ( frangi_a > 0 ) vp.setFrangi_a( (float) frangi_a );
					if ( frangi_b > 0 ) vp.setFrangi_b( (float) frangi_b );
					if ( frangi_c > 0 ) vp.setFrangi_c( (float) frangi_c );
				}
				if (method==2) vp.setSatoAlpha( (float) sato_alpha );
				ImagePlus result = vp.generateImage(original);
				
				result.setTitle(title + " vessel image (sigma " + String.valueOf(sigma) + ")");
				StackStatistics stats = new StackStatistics(result);
				result.setDisplayRange(stats.min, stats.max);
				
				//stats = new StackStatistics(result, 256, stats.min, stats.max);
				//int[] histogram = stats.histogram;
				//System.out.println(sigma + " : pre histo bin: " + histogram.length);
				//System.out.println(sigma + " : pre maxCount: " + histogram[histogram.length-1]);
				//StackConverter sc = new StackConverter();
				if (convertToGray8)
					new StackConverter(result).convertToGray8();
				tubeImps.add(result);
				if (saveScaleImage) {
					//result.show();
					new FileSaver(result).saveAsTiffStack(saveDirPath + result.getTitle() + ".tif");
				}
				
				result.close();
				System.gc();
				//stats = new StackStatistics(result);
				//histogram = stats.histogram;
				//System.out.println(sigma + " : post maxCount: " + histogram[histogram.length-1]);
				//sigma = sigma/Math.sqrt(2);
				//System.out.println(sigma);
			}
	
			//double duration = System.currentTimeMillis() - start;
			//System.out.println(duration/1000+" second");
			//start = System.currentTimeMillis();
	
			ImageStack stack = new ImageStack(width, height, depth);
			ImageStack selStack = new ImageStack(width, height, depth);
	
			
			//double normFactor = pixelSize * pixelSize * dataDynamicRange * 0.3;	// normalize the pixel value to [0, 1];
			
			IJ.showStatus("Scale selection...");
			for (int z=0; z<depth; z++) {
				IJ.showProgress( (double) z / (double) depth);
				ImageProcessor ip;
				ByteProcessor selIp = new ByteProcessor(width, height);
				//FloatProcessor fp = new FloatProcessor(width, height);
				if (convertToGray8)
					ip = new ByteProcessor(width, height);
				else
					ip = new FloatProcessor(width, height);
				for (int y=0; y<height; y++) {
					for (int x=0; x<width; x++) {
						double value = 0;
						for (int i=0; i<tubeImps.size(); i++) {
							//value = Math.max(value, imp.getStack().getVoxel(x, y, z));
							if (value < tubeImps.get(i).getStack().getVoxel(x, y, z)) {
								value = tubeImps.get(i).getStack().getVoxel(x, y, z);
								if (saveSelectionImage) selIp.set(x, y, i+1);
							}
						}
						if (convertToGray8)
							value = (int) value;
						ip.putPixelValue(x, y, value);
					}
				}
				stack.setProcessor(ip, z+1);
				if (saveSelectionImage) selStack.setProcessor(selIp, z+1);
			}
			IJ.showProgress( 1.0 );
			//duration = System.currentTimeMillis() - start;
			//System.out.println(duration/1000+" second");
			
			ImagePlus tubenessImage = new ImagePlus(title + " vesselness", stack);
					
			StackStatistics stats = new StackStatistics(tubenessImage);
			tubenessImage.setDisplayRange(stats.min, stats.max);
			
			new FileSaver(tubenessImage).saveAsTiffStack(saveDirPath + tubenessImage.getTitle() + ".tif");
			
			if (tubenessImage.getStackSize()>1 && saveProjection) {
				ImagePlus projectionImage = ZProjector.run(tubenessImage, "max all");
				projectionImage.setTitle(tubenessImage.getTitle() +" projection");
				new FileSaver(projectionImage).saveAsTiff(saveDirPath + projectionImage.getTitle() + ".tif");
				//projectionImage.show();
			}
			
			if (tubenessImage.getStackSize()>1 && saveSelectionImage) {
				ImagePlus selectionImage = new ImagePlus(tubenessImage.getTitle() + " scale selection", selStack);
				selectionImage.getProcessor().setMinAndMax(0, tubeImps.size());
				new FileSaver(selectionImage).saveAsTiffStack(saveDirPath + selectionImage.getTitle() + ".tif");
				//selectionImage.show();
			}
			
			System.gc();
			
			//double duration = System.currentTimeMillis() - start;
			//IJ.showStatus("Skeleton generation finished after " + String.valueOf(duration) + " second.");
		}
		
		//double duration = System.currentTimeMillis() - start;
		//IJ.showStatus("Skeleton generation finished after " + String.valueOf(duration) + " second.");
		
	}

}
