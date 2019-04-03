package uk.ac.cam.cruk.RSOM;

import java.io.File;
import org.apache.commons.io.FilenameUtils;

import ij.IJ;
import ij.ImagePlus;
import ij.measure.Calibration;
import ij.plugin.FolderOpener;
public class RsomImageStack {

	protected ImagePlus RSOMImg;
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
	protected String maskPath = null;
	protected String objectMapPath = null;
	protected String roiPath = null;
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
		if (imgF.isDirectory())	isSequence = true;
		//ImagePlus RSOMImg;
		if (isSequence) {
			RSOMImg = FolderOpener.open(filePath,"file=tif");
		} else {
			RSOMImg = IJ.openImage(filePath);
		}
		if (RSOMImg == null) {
			imageOnDisk = false;
			return;
		}
		imageOnDisk = true;
		dimensions[0] = sizeX = RSOMImg.getWidth();
		dimensions[1] = sizeY = RSOMImg.getHeight();
		dimensions[2] = sizeZ = RSOMImg.getNSlices();
		imageTitle = RSOMImg.getTitle();
		
		Calibration cal = RSOMImg.getCalibration();
		if (cal.scaled()) {
			calibrated = true;
		}
		calibration = new double[] {cal.pixelWidth, cal.pixelHeight, cal.pixelDepth};
		calUnit = cal.getUnit();
		if (sizeX != sizeY || cal.pixelWidth != cal.pixelHeight) {
			isTransposed = true;
		}
		
		imageName = FilenameUtils.removeExtension(imageTitle);
		imageName = nameValid(imageName);
		resultDir = dirPath + File.separator + imageName + "_result";
		File resultF = new File(resultDir);
		if (resultF.exists()) {
			resultDirExist = true;
			executionLogPath =resultDir + File.separator + "execution_log.csv";
			maskPath = resultDir + File.separator + imageName + "_mask.zip";
			objectMapPath = resultDir + File.separator + imageName + "_objectMap.tiff";
			roiPath = resultDir + File.separator + imageName + "_Roiset.zip";
			selectionPath = resultDir + File.separator + imageName + "_selection3D.zip";
			diameterMapPath = resultDir + File.separator + imageName + "_diameterMap.tiff";
			resultSlicePath = resultDir + File.separator + imageName + "_slice-wise.csv";
			resultRoiPath = resultDir + File.separator + imageName + "_ROI-wise.csv";
			resultObjectPath = resultDir + File.separator + imageName + "_object-wise.csv";
			resultHistogramPath = resultDir + File.separator + imageName + "_histogram.jpg";
		}
		return;
	}
	
	// constructor overload?? object constructor from image plus instance
	public RsomImageStack (ImagePlus imp) {
		RSOMImg = imp;
		dimensions[0] = sizeX = RSOMImg.getWidth();
		dimensions[1] = sizeY = RSOMImg.getHeight();
		dimensions[2] = sizeZ = RSOMImg.getNSlices();
		
		Calibration cal = RSOMImg.getCalibration();
		if (cal.scaled()) {
			calibrated = true;
		}
		calibration = new double[] {cal.pixelWidth, cal.pixelHeight, cal.pixelDepth};
		calUnit = cal.getUnit();
		if (sizeX != sizeY || cal.pixelWidth != cal.pixelHeight) {
			isTransposed = true;
		}
		
		imageOnDisk = onDisk(RSOMImg);
		if (!imageOnDisk) {
			return; //image not on disk, return
		}
		
		RSOMImg.setActivated();
		imageTitle = RSOMImg.getTitle();
		dirPath = IJ.getDirectory("image"); //with file separator
		filePath = dirPath + imageTitle; //doesn't work with .zip images
		
		// image sequence recognised as no file with name found
		File imgF = new File(filePath);
		if (imgF.exists()) {
			isSequence = false;
			imageName = FilenameUtils.removeExtension(imageTitle);
		} else {
			isSequence = true;
			//filePath = dirPath; //this way , file name with a file separator in the end
			imgF = new File(dirPath);
			dirPath = imgF.getParent()+File.separator;
			filePath = dirPath + imageTitle;
			imageName = imageTitle;
		}	
		imageName = nameValid(imageName);
		resultDir = dirPath + imageName + "_result";
		File resultF = new File(resultDir);
		if (resultF.exists()) {
			resultDirExist = true;
			executionLogPath =resultDir + File.separator + "execution_log.csv";
			maskPath = resultDir + File.separator + imageName + "_mask.zip";
			objectMapPath = resultDir + File.separator + imageName + "_objectMap.tiff";
			roiPath = resultDir + File.separator + imageName + "_Roiset.zip";
			selectionPath = resultDir + File.separator + imageName + "_selection3D.zip";
			diameterMapPath = resultDir + File.separator + imageName + "_diameterMap.tiff";
			resultSlicePath = resultDir + File.separator + imageName + "_slice-wise.csv";
			resultRoiPath = resultDir + File.separator + imageName + "_ROI-wise.csv";
			resultObjectPath = resultDir + File.separator + imageName + "_object-wise.csv";
			resultHistogramPath = resultDir + File.separator + imageName + "_histogram.png";
		}
		return;
	}
	
	public String nameValid (
			String imgName) {
		if ((imgName == "") || (imgName == null)) {
			imgName = IJ.getString("Name the current RSOM image stack", "filtered_dataset");
		}
		return imgName;
	}
	
	public boolean onDisk(ImagePlus imp) {
		imp.setActivated();
		String imgDir = IJ.getDirectory("image");
		if (imgDir == null)	{
			imageOnDisk = false;
		} else {
			imageOnDisk = true;
		}
		return imageOnDisk;
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
