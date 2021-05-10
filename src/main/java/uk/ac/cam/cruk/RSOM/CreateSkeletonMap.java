package uk.ac.cam.cruk.RSOM;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.plugin.Duplicator;
import ij.plugin.Filters3D;
import ij.plugin.PlugIn;
import ij.plugin.ZProjector;
import ij.process.ImageProcessor;
import ij.process.StackStatistics;
import inra.ijpb.morphology.Morphology;
import inra.ijpb.morphology.Strel;
import inra.ijpb.morphology.Strel3D;
import inra.ijpb.morphology.strel.BallStrel;
import sc.fiji.skeletonize3D.Skeletonize3D_;
import features.TubenessProcessor;
import fiji.util.gui.GenericDialogPlus;

//import Skeletonize3D_;
//import sc.fiji.skeletonize3D.Skeletonize3D_;
//import fiji-lib.skeletonize3D;

/*
 * plugin to create skeleton map based on binary mask (2D / 3D);
 * 
 * future implementation: 8-bit gray image / 16/32 bit float image
 */
public class CreateSkeletonMap implements PlugIn {

	//protected ImagePlus inputMask;
	//protected double[] sigmaSet;
	//protected boolean useCalibration;
	//protected final int maxIteration = 20;
	
	@Override
	public void run(String arg0) {
		
		GenericDialogPlus gd = new GenericDialogPlus("Create skeleton from 3D mask");
		gd.addImageChoice("active image list", "");
		gd.addCheckbox("load image from disk", false);
		gd.addFileField("path", "");	
		gd.addCheckbox("pre-smoothing", true);
		gd.addNumericField("maximum smoothing iteration:", 20, 0);
		gd.addNumericField("skeleton width", 1, 0, 2, "voxel");
		gd.addCheckbox("show 2D max projection image", true);
		gd.showDialog();
		if (gd.wasCanceled()) return;
		ImagePlus activeImage = null;
		try {
			activeImage = gd.getNextImage();
		} catch (ArrayIndexOutOfBoundsException e) {}
		boolean loadImage = gd.getNextBoolean();
		String loadPath = gd.getNextString();
		boolean doPreSmooth = gd.getNextBoolean();
		int maxIteration = (int)gd.getNextNumber();
		double skeletonWidth = gd.getNextNumber();
		boolean showProjection = gd.getNextBoolean();

		ImagePlus impOri = loadImage ? IJ.openImage(loadPath) : activeImage;
		if (impOri==null || !impOri.getProcessor().isBinary()) return;
		

		double start = System.currentTimeMillis();
		//TubenessProcessor tp = new TubenessProcessor(sigmaSet[0],useCalibration);
		//ImagePlus result = tp.generateImage(original);

		
		impOri.show();
		ImagePlus inputMask = impOri.duplicate();
		
		ImageStack stack = inputMask.getStack();
		StackStatistics stats = new StackStatistics(inputMask);
		double voxelCount = stats.pixelCount/255;
		if (doPreSmooth) {
			int i = 0;
			double signalCount = voxelCount;
			while (i<maxIteration) {
				IJ.showStatus("Smoothing iteration " + String.valueOf(++i));
				if (signalCount >= stats.mean*voxelCount)
					signalCount = stats.mean*voxelCount;
				else
					break;
				stack = Filters3D.filter(stack, Filters3D.MEDIAN, 1.0f, 1.0f, 1.0f); 
				inputMask.setStack(stack);
				stats = new StackStatistics(inputMask);
			}
		}
		Skeletonize3D_ s3d = new Skeletonize3D_();
		s3d.setup("", inputMask);
		s3d.run(inputMask.getProcessor());
				
		if (skeletonWidth>1)
			inputMask = new ImagePlus("", Morphology.dilation(inputMask.getStack(), BallStrel.fromDiameter(skeletonWidth)));

		
		inputMask.setTitle(impOri.getTitle() + " skeleton");
		if (impOri.getStackSize()>1 && showProjection) {
			ImagePlus projectionImage = ZProjector.run(inputMask, "max all");
			projectionImage.setTitle(inputMask.getTitle() +" projection");
			projectionImage.show();
		}
		inputMask.changes = true;
		inputMask.show();

		double duration = System.currentTimeMillis() - start;
		IJ.showStatus("Skeleton generation finished after " + String.valueOf(duration) + " second.");
		
	}
	
}
