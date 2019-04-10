package uk.ac.cam.cruk.RSOM;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;

import org.apache.commons.io.FilenameUtils;

import ij.IJ;
import ij.ImagePlus;
import ij.io.FileSaver;
import ij.measure.Calibration;
import ij.plugin.FolderOpener;
public class RsomImageStack {

	protected ImagePlus RSOMImg;
	protected String sampleCode;
	protected String imageTitle = null; //with extension
	protected String imageName = null;
	protected int sizeX, sizeY, sizeZ;
	protected int[] dimensions = new int[3];
	protected boolean calibrated = false;
	protected double[] calibration = new double[3];
	protected String calUnit = null;
	protected boolean isTransposed = false;
	
	protected boolean isSequence = false;
	protected boolean imageOnDisk = false;
	protected String filePath = null;
	protected String dirPath = null;
	//processed;
	protected String resultDir = null;
	protected boolean resultDirExist = false;
	protected String executionLogPath = null;
	
	// account for multiple results in [mask, object, diametermap, object2D
	protected String maskPath = null;
	protected String objectMapPath = null;
	protected String roiPath = null;
	protected String manualROIPath = null;
	protected String selectionPath = null;
	protected String diameterMapPath = null;
	protected String resultSlicePath = null;
	protected String resultRoiPath = null;
	protected String resultObjectPath = null;
	protected String resultHistogramPath = null;
	
	// object constructor from path string
	public RsomImageStack (String path) {
		if (path==null) return;
		filePath = path;
		File imgF = new File(filePath);
		dirPath = imgF.getParent();
		if (imgF.isDirectory())	{
			if (isImageSequence(imgF)) {
				isSequence = true;
				dirPath = new File(dirPath).getParent() + File.separator;
			}
			else return;
		}
		
		// if input is a image sequence, first convert it to tif stack,
		// and then construct the RSOM image with the tif stack
		ImagePlus imp = null;
		if (isSequence) { 
			imp = FolderOpener.open(filePath,"file=tif");
		} else {
			imp = IJ.openImage(filePath);
		}
		sampleCode = getRSOMSampleCode(path);
		if (sampleCode==null) {
			sampleCode = IJ.getString("Name the current RSOM image stack with time and date Code(e.g.: R_154837_20180312)", "R_");
		}
		if (isSequence || !imp.getShortTitle().equals(sampleCode)) {
			FileSaver fs = new FileSaver(imp);
			filePath = dirPath + File.separator + sampleCode + ".tif";
			fs.saveAsTiffStack(dirPath + File.separator + sampleCode + ".tif");
			imp.close();
			IJ.run("Collect Garbage", "");
			System.gc();
			RSOMImg = IJ.openImage(filePath);
			isSequence = false;	// added on 2019/04/03 as no image sequence supposed to be processed in the future
		} else {
			RSOMImg = imp;
		}
		
		checkImageInfo();
		checkResultDir();
		return;
	}
	
	// constructor overload?? object constructor from image plus instance
	public RsomImageStack (ImagePlus imp) {
		
		RSOMImg = imp;
		imageOnDisk = onDisk(RSOMImg);
		if (!imageOnDisk) {
			IJ.log("Image " + imp.getTitle() + " not found on computer. Operation Abort.");
			IJ.log("Save this image to a proper location on computer first.");
			return; //image not on disk, return
		}
		RSOMImg.setActivated();
		
		imageTitle = imp.getTitle();
		dirPath = IJ.getDirectory("image"); //with file separator
		filePath = dirPath + imageTitle; //doesn't work with .zip images
		sampleCode = getRSOMSampleCode(filePath);
		if (sampleCode==null) {
			sampleCode = IJ.getString("Name the current RSOM image stack with time and date Code(e.g.: R_154837_20180312)", "R_");
		}
		// image sequence recognized as no file with name found
		File imgF = new File(filePath);
		if (imgF.exists()) {
			isSequence = false;
		} else {
			isSequence = true;
			dirPath = new File(dirPath).getParent() + File.separator;
		}
		if (isSequence || !imp.getShortTitle().equals(sampleCode)) {
			FileSaver fs = new FileSaver(imp);
			filePath = dirPath + File.separator + sampleCode + ".tif";
			fs.saveAsTiffStack(filePath);
			imp.close();
			IJ.run("Collect Garbage", "");
			System.gc();
			RSOMImg = IJ.openImage(filePath);
			isSequence = false;	// added on 2019/04/03 as no image sequence supposed to be processed in the future
		}
		
		checkImageInfo();
		checkResultDir();
		return;
	}
	
	public void checkImageInfo () {
		dimensions[0] = sizeX = RSOMImg.getWidth();
		dimensions[1] = sizeY = RSOMImg.getHeight();
		dimensions[2] = sizeZ = RSOMImg.getNSlices();
		imageTitle = RSOMImg.getTitle();
		imageName = RSOMImg.getShortTitle();
		
		Calibration cal = RSOMImg.getCalibration();
		if (cal.scaled()) {
			calibrated = true;
		}
		calibration = new double[] {cal.pixelWidth, cal.pixelHeight, cal.pixelDepth};
		calUnit = cal.getUnit();
		if (sizeX != sizeY || cal.pixelWidth != cal.pixelHeight) {
			isTransposed = true;
		}
	}
	
	public void checkResultDir () {
		resultDir = dirPath + File.separator + sampleCode + "_result";
		File resultF = new File(resultDir);
		if (resultF.exists()) {
			resultDirExist = true;
			executionLogPath =resultDir + File.separator + "execution_log.csv";
			maskPath = resultDir + File.separator + sampleCode + "_mask.zip";
			objectMapPath = resultDir + File.separator + sampleCode + "_objectMap.tiff";
			roiPath = resultDir + File.separator + sampleCode + "_object2D.zip";
			manualROIPath = resultDir + File.separator + sampleCode + "_manualROI.zip";
			selectionPath = resultDir + File.separator + sampleCode + "_selection3D.zip";
			diameterMapPath = resultDir + File.separator + sampleCode + "_diameterMap.tiff";
			resultSlicePath = resultDir + File.separator + sampleCode + "_slice-wise.csv";
			resultRoiPath = resultDir + File.separator + sampleCode + "_object2D-wise.csv";
			resultObjectPath = resultDir + File.separator + sampleCode + "_object3D-wise.csv";
			resultHistogramPath = resultDir + File.separator + sampleCode + "_histogram.png";
		}
	}
	
	public boolean isImageSequence (
			File input
			) {	
			if (!input.isDirectory())
				return false;
			File[] tifFileList = input.listFiles(new FilenameFilter() {
				public boolean accept(
					File dir, String filename
					) {
					boolean ext1 = filename.endsWith(".tif");
					boolean ext2 = filename.endsWith(".tiff");
					return (ext1 || ext2);	
					}
				});
			if (tifFileList.length < 2)
				return false;
			int nameLength	= tifFileList[0].getName().length();
			for (int i=1; i<tifFileList.length; i++) {
				if (tifFileList[i].getName().length() != nameLength)
					return false;
			}
			return true;
	}
	
	public String getRSOMSampleCode (
			String fileName
			) {
		//String fileName = imageSequence.getCanonicalPath();
		int timeCodeBeginIdx = fileName.lastIndexOf("R_");
		if (timeCodeBeginIdx == -1) return null;

		int dateCodeBeginIdx = fileName.indexOf("201");
		if (dateCodeBeginIdx == -1) return null;
		
		String timeCode = fileName.substring(timeCodeBeginIdx+2,timeCodeBeginIdx+8);
		String dateCode = fileName.substring(dateCodeBeginIdx,dateCodeBeginIdx+8);
		
		return ("R_"+timeCode+"_"+dateCode);
	}
	
	public String nameValid (
			String imgName) {
		if ((imgName == "") || (imgName == null)) {
			imgName = IJ.getString("Name the current RSOM image stack with time and date Code(e.g.: R_154837_20180312)", "R_");
		}
		return imgName;
	}
	
	public boolean onDisk(ImagePlus imp) {
		imp.setActivated();
		String imgDir = IJ.getDirectory("image");
		if (imgDir == null)	{
			return false;
		} 
		return true;
	}
	
	public static void calibrateImage(
			RsomImageStack r,
			double x,
			double y,
			double z,
			String unit
			) {
		r.RSOMImg.getCalibration().pixelWidth = x;
		r.RSOMImg.getCalibration().pixelHeight = y;
		r.RSOMImg.getCalibration().pixelDepth = z;
		r.RSOMImg.getCalibration().setUnit(unit);
		r.calibrated = true;
		r.calibration = new double[] {x, y, z};
		r.calUnit = unit;
		// need update to show the new calibration in image status bar
		// imp.updateAndRepaintWindow();
	}
}
