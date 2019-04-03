package uk.ac.cam.cruk.RSOM;

//import uk.ac.cam.cruk.Cursor3D;

import java.util.ArrayList;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.plugin.filter.ParticleAnalyzer;
import ij.plugin.frame.RoiManager;
import ij.process.ImageStatistics;

public class ConnectComponentAnalysis {

	protected ImagePlus mask;
	protected ImageStack maskStack;
	protected ImageStack objStack;
	protected int connectivity = 26;
	protected int bitDepth = 16;

	// default constructors
	public ConnectComponentAnalysis () {}
	public ConnectComponentAnalysis (int connectivity) {
		this.connectivity = connectivity;
	}
	public ConnectComponentAnalysis(int connectivity, int bitDepth) {
	    this.connectivity = connectivity;
	    this.bitDepth = bitDepth;
	}
	public ConnectComponentAnalysis(ImagePlus mask, int connectivity, int bitDepth) {
		this.mask = mask;
	    this.connectivity = connectivity;
	    this.bitDepth = bitDepth;
	    this.maskStack = mask.getStack();
	}
	
	
	
	public static void backgroundToNaN(
			ImagePlus imp,
			final int backgroundColor
			) {
		if (imp == null || !imp.isStack()) return;
		if (imp.getBitDepth() != 32) {
			IJ.log("Image " + imp.getTitle() + " is not 32-bit, Can not set background to NaN.");
			return;
		}
		
		final int depth = imp.getNSlices();
		final int pixelsPerSlice = imp.getWidth() * imp.getHeight();
		final ImageStack stack = imp.getStack();
		for (int z = 1; z <= depth; z++) {
			float[] pixels = (float[]) stack.getPixels(z);
			for (int i = 0; i < pixelsPerSlice; i++) {
				if (Float.compare(pixels[i], backgroundColor) == 0) {
					pixels[i] = Float.NaN;
				}
			}
			stack.getProcessor(z).resetMinAndMax();
		}
		return;
	}
	
	public static RoiManager CC_2D (
			ImagePlus inputImage
			) {
		Boolean saveBlackBackground = Prefs.blackBackground;
		Prefs.blackBackground = true;
		
		RoiManagerUtility.resetManager();
		RoiManager rm = RoiManager.getInstance2();
		RoiManagerUtility.hideManager();
		
		ImageStatistics stats = inputImage.getStatistics();
		
		if ((inputImage.getBitDepth() != 8) || 
				(stats.histogram[0]+stats.histogram[255]!=stats.pixelCount)) {
			IJ.error("8-bit binary image is required for connected component analysis.");
			Prefs.blackBackground = saveBlackBackground;
			return null;
		}
		int z = inputImage.getNSlices();
		ResultsTable rt = new ResultsTable(); 
		Double min = new Double(1);
		ParticleAnalyzer pa = new ParticleAnalyzer(ParticleAnalyzer.ADD_TO_MANAGER, Measurements.AREA, rt, min, Double.POSITIVE_INFINITY);

		/*
		for (int i=1; i<inputImage.getNSlices()+1; i++) {
			inputImage.setZ(i);
			inputImage.getProcessor().invert();
		}
		*/
		for (int i=1; i<z+1; i++) {
			inputImage.setSlice(i);
			pa.analyze(inputImage);
		}
		/*
		for (int i=1; i<inputImage.getNSlices()+1; i++) {
			inputImage.setZ(i);
			inputImage.getProcessor().invert();
		}
		*/
		//return RoiManager.getRoiManager();
		Prefs.blackBackground = saveBlackBackground;
		return rm;
	}
	
	public static RoiManager CC_2D ( //need to be implemented for different connectivity, the same as CC_2D(imp)
			ImagePlus inputImage,
			int connectivity
			) {
		Boolean saveBlackBackground = Prefs.blackBackground;
		Prefs.blackBackground = true;
		
		RoiManagerUtility.resetManager();
		RoiManager rm = RoiManager.getInstance2();
		RoiManagerUtility.hideManager();
		
		ImageStatistics stats = inputImage.getStatistics();
		
		if ((inputImage.getBitDepth() != 8) || 
				(stats.histogram[0]+stats.histogram[255]!=stats.pixelCount)) {
			IJ.error("8-bit binary image is required for connected component analysis.");
			Prefs.blackBackground = saveBlackBackground;
			return null;
		}
		int z = inputImage.getNSlices();
		ResultsTable rt = new ResultsTable(); 
		Double min = new Double(1);
		
		ParticleAnalyzer pa = new ParticleAnalyzer(ParticleAnalyzer.ADD_TO_MANAGER, Measurements.AREA, rt, min, Double.POSITIVE_INFINITY);
		
		for (int i=1; i<z+1; i++) {
			inputImage.setSlice(i);
			pa.analyze(inputImage);
		}
		
		Prefs.blackBackground = saveBlackBackground;
		return rm;
	}
	
	public ImagePlus CC_3D (
			ImagePlus mask,
			int connectivity,
			int bitDepth,
			String imgName
			) {
	
		Boolean saveBlackBackground = Prefs.blackBackground;
		Prefs.blackBackground = true;
		
		ImageStack sourceStack = mask.getStack();
		int sizeX = sourceStack.getWidth();
		int sizeY = sourceStack.getHeight();
		int sizeZ = sourceStack.getSize();
		ImageStack targetStack = ImageStack.create(sizeX,sizeY,sizeZ,bitDepth);
		
		int nLabels = 0;
		
		for (int z = 0; z < sizeZ; z++) {
			for (int y = 0; y < sizeY; y++) {
				for (int x = 0; x < sizeX; x++) {
					// Do not process background voxels
					if (sourceStack.getVoxel(x, y, z) == 0)
						continue;
					// Do not process voxels already labeled
					if (targetStack.getVoxel(x, y, z) > 0)
						continue;
					// increment label index, and propagate
					nLabels++;
					// flood fill algorithm taken from MorpholibJ floodFillC26 and floodFillC6
					// https://github.com/ijpb/MorphoLibJ/blob/master/src/main/java/inra/ijpb/morphology/FloodFill3D.java
					if (connectivity == 26) {
						floodFillC26(sourceStack, x, y, z, targetStack, nLabels);
					} else if (connectivity == 6) {
						floodFillC6(sourceStack, x, y, z, targetStack, nLabels);
					} else {
						IJ.log("Error! Wrong connectivity value: " + String.valueOf(connectivity) + " !"); 
						return null;
					}
				}
			}
		}
		ImagePlus objImg = new ImagePlus(imgName + "_objectMap",targetStack);
		Prefs.blackBackground = saveBlackBackground;
		backgroundToNaN(objImg, 0x00);
		return objImg;
	}
	
	public static void fillLineInt(
			ImageStack is,
			int x1,
			int x2,
			int y,
			int z,
			int value) {
		for (int x = x1; x <= x2; x++)
			is.setVoxel(x, y, z, value);
	}
	
	public void floodFillC6 (
			ImageStack inputImage,
			int x,
			int y,
			int z,
			ImageStack outputImage,
			int value) {
			// get image size
		int sizeX = inputImage.getWidth();
		int sizeY = inputImage.getHeight();
		int sizeZ = inputImage.getSize();
		
		// get old value
		int oldValue = (int) inputImage.getVoxel(x, y, z);
				
		// initialize the stack with original pixel
		ArrayList<Cursor3D> stack = new ArrayList<Cursor3D>();
		stack.add(new Cursor3D(x, y, z));
		
		boolean inScanLine;
		
		// process all items in stack
		while (!stack.isEmpty()) {
			// Extract current position
			Cursor3D p = stack.remove(stack.size()-1);
			x = p.x;
			y = p.y;
			z = p.z;
			
			// process only pixel of the same value
			if ((int) inputImage.getVoxel(x, y, z) != oldValue)
				continue;
			
			// x extremities of scan-line
			int x1 = x; 
			int x2 = x;
			
			// find start of scan-line
			while (x1 > 0 && (int) inputImage.getVoxel(x1 - 1, y, z) == oldValue)
				x1--;

			// find end of scan-line
			while (x2 < sizeX - 1 && (int) inputImage.getVoxel(x2 + 1, y, z) == oldValue)
				x2++;

			// fill current scan-line
			fillLineInt(outputImage, x1, x2, y, z, value);

			// search bounds on x axis for neighbor lines
			int x1l = Math.max(x1, 0);
			int x2l = Math.min(x2, sizeX - 1);

			// find scan-lines above the current one
			if (y > 0)
			{
				inScanLine = false;
				for (int i = x1l; i <= x2l; i++)
				{
					int val = (int) inputImage.getVoxel(i, y - 1, z);
					int lab = (int) outputImage.getVoxel(i, y - 1, z);

					if (!inScanLine && val == oldValue && lab != value)
					{
						stack.add(new Cursor3D(i, y - 1, z));
						inScanLine = true;
					} 
					else if (inScanLine && val != oldValue)
					{
						inScanLine = false;
					}
				}
			}

			// find scan-lines below the current one
			if (y < sizeY - 1)
			{
				inScanLine = false;
				for (int i = x1l; i <= x2l; i++)
				{
					int val = (int) inputImage.getVoxel(i, y + 1, z);
					int lab = (int) outputImage.getVoxel(i, y + 1, z);

					if (!inScanLine && val == oldValue && lab != value)
					{
						stack.add(new Cursor3D(i, y + 1, z));
						inScanLine = true;
					} 
					else if (inScanLine && val != oldValue)
					{
						inScanLine = false;
					}
				}
			}

			// find scan-lines in front of the current one
			if (z > 0)
			{
				inScanLine = false;
				for (int i = x1l; i <= x2l; i++)
				{
					int val = (int) inputImage.getVoxel(i, y, z - 1);
					int lab = (int) outputImage.getVoxel(i, y, z - 1);

					if (!inScanLine && val == oldValue && lab != value)
					{
						stack.add(new Cursor3D(i, y, z - 1));
						inScanLine = true;
					}
					else if (inScanLine && val != oldValue)
					{
						inScanLine = false;
					}
				}
			}

			// find scan-lines behind the current one
			if (z < sizeZ - 1)
			{
				inScanLine = false;
				for (int i = x1l; i <= x2l; i++)
				{
					int val = (int) inputImage.getVoxel(i, y, z + 1);
					int lab = (int) outputImage.getVoxel(i, y, z + 1);

					if (!inScanLine && val == oldValue && lab != value)
					{
						stack.add(new Cursor3D(i, y, z + 1));
						inScanLine = true;
					} 
					else if (inScanLine && val != oldValue)
					{
						inScanLine = false;
					}
				}
			}
		}
	}
	
	public void floodFillC26 (
			ImageStack inputImage,
			int x,
			int y,
			int z,
			ImageStack outputImage,
			int value) {
			// get image size
		int sizeX = inputImage.getWidth();
		int sizeY = inputImage.getHeight();
		int sizeZ = inputImage.getSize();
		
		// get old value
		double oldValue = inputImage.getVoxel(x, y, z);
				
		// initialize the stack with original pixel
		ArrayList<Cursor3D> stack = new ArrayList<Cursor3D>();
		stack.add(new Cursor3D(x, y, z));
		
		boolean inScanLine;
		
		// process all items in stack
		while (!stack.isEmpty()) {
			// Extract current position
			Cursor3D p = stack.remove(stack.size()-1);
			x = p.x;
			y = p.y;
			z = p.z;
			
			// process only pixel of the same value
			if (inputImage.getVoxel(x, y, z) != oldValue)
				continue;
			
			// x extremities of scan-line
			int x1 = x; 
			int x2 = x;
			
			// find start of scan-line
			while (x1 > 0 && inputImage.getVoxel(x1-1, y, z) == oldValue)
				x1--;
			
			// find end of scan-line
			while (x2 < sizeX - 1 && inputImage.getVoxel(x2+1, y, z) == oldValue)
				x2++;
		
			// fill current scan-line
			fillLineInt(outputImage, x1, x2, y, z, value);
			
			// search bounds on x axis for neighbor lines
			int x1l = Math.max(x1 - 1, 0);
			int x2l = Math.min(x2 + 1, sizeX - 1);

			// check the eight X-lines around the current one
			for (int z2 = Math.max(z - 1, 0); z2 <= Math.min(z + 1, sizeZ - 1); z2++) {
				for (int y2 = Math.max(y - 1, 0); y2 <= Math.min(y + 1, sizeY - 1); y2++) {
					// do not process the middle line
					if (z2 == z && y2 == y)
						continue;
					
					inScanLine = false;
					for (int i = x1l; i <= x2l; i++) {
						double val = inputImage.getVoxel(i, y2, z2);
						double lab = outputImage.getVoxel(i, y2, z2);
						
						if (!inScanLine && val == oldValue && lab != value) {
							stack.add(new Cursor3D(i, y2, z2));
							inScanLine = true;
						} 
						else if (inScanLine && val != oldValue) {
							inScanLine = false;
						}	
					}
				}
			}
		}
	}

	
	
	
	
}
