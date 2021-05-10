package uk.ac.cam.cruk.RSOM;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import org.apache.commons.io.FilenameUtils;

import ij.IJ;
import ij.ImagePlus;
import ij.io.FileSaver;
import ij.measure.Calibration;
import ij.plugin.FolderOpener;
public class RsomImageStack {

	protected ImagePlus RSOMImg;
	protected String sampleCode;
	// protected final String pattern = "[A-Z]_\\d{6}_\\d{8}_[a-zA-Z]+\\d+";	// old name convention e.g.: R_154837_20180312_XX
	protected final String pattern = "[A-Z]_\\d{6}_\\S+";	// new name rule e.g.: R_000000_????
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
	protected String dirPath = null;	// should always ends with file.separator
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
		if (!dirPath.endsWith(File.separator)) {
			dirPath += File.separator;
		}
		
		ImagePlus imp = null;
		
		sampleCode = getRSOMSampleCode2(path);
		
		// 2021.02.01 modification remove name constrain
		/*
		if (sampleCode==null) {	// can not guess sample code (e.g.: filtered_dataset in Download folder
			sampleCode = IJ.getString("Name the current RSOM image stack with corresponding time and date Code(e.g.: R_154837_20180312)", "R_");
		}
		*/
		
		System.out.println("debug: filePath: "+ filePath);
		System.out.println("debug: dirPath: "+ dirPath);
		System.out.println("debug: samplecode: "+ sampleCode);
		
		if (imgF.isDirectory())	{
			if (isImageSequence(imgF)) {
				// if input is a image sequence, first convert it to tif stack,
				// and then construct the RSOM image with the tif stack
				ImagePlus impSequence = FolderOpener.open(filePath,"file=tif");
				FileSaver fs = new FileSaver(impSequence);
				filePath = dirPath + sampleCode + ".tif";
				fs.saveAsTiffStack(filePath);
				//RSOMImg = imp
				impSequence.close();
				IJ.run("Collect Garbage", "");
				System.gc();
				imp = IJ.openImage(filePath);
				//RSOMImg = IJ.openImage(filePath);
				//dirPath = new File(dirPath).getParent() + File.separator;
			} else {
				IJ.log("input does not appear to be an image file.");
				return;
			}
		} else {
			imp = IJ.openImage(filePath);
		}
		
		if (!imp.isStack()) {
			IJ.log("Image " + imp.getTitle() + " does not appear to be a RSOM image stack. Operation Abort.");
			return;
		}
		
		RSOMImg = imp;
		/*
		if (imp.getShortTitle().equals(sampleCode)){ 
			RSOMImg = imp;
		} else {
			//return;
			FileSaver fs = new FileSaver(imp);
			filePath = dirPath + sampleCode + ".tif";
			fs.saveAsTiffStack(filePath);
			//RSOMImg = imp
			imp.close();
			IJ.run("Collect Garbage", "");
			System.gc();
			RSOMImg = IJ.openImage(filePath);
		}
		*/
		
		//RSOMImg = imp;
		
		/*
		if (imp.getShortTitle().equals(sampleCode)){ 
			RSOMImg = imp;
		} else {
			return;
		}
		*/
		
		checkImageInfo();
		checkResultDir();
		return;
	}
	
	// constructor overload?? object constructor from image plus instance
	public RsomImageStack (ImagePlus imp) {
		
		//RSOMImg = imp;
		if (!imp.isStack()) {
			IJ.log("Image " + imp.getTitle() + " does not seem to be a RSOM image stack. Operation Abort.");
			return;
		}
		imageOnDisk = onDisk(imp);
		if (!imageOnDisk) {
			IJ.log("Image " + imp.getTitle() + " not found on computer. Operation Abort.");
			IJ.log("Save this image to a proper location on computer first.");
			return; //image not on disk, return
		}
		imp.setActivated();
		
		imageTitle = imp.getTitle();
		dirPath = IJ.getDirectory("image"); //with file separator
		if (!dirPath.endsWith(File.separator)) {
			dirPath += File.separator;
		}
		filePath = dirPath + imageTitle; //doesn't work with .zip images
		
		// from here on construct the RSOM object with image
		sampleCode = getRSOMSampleCode(filePath);
		
		// 2021.02.01 modification remove name constrain
		if (sampleCode==null) {
			//sampleCode = IJ.getString("Name the current RSOM image stack with time and date Code(e.g.: R_154837_20180312)", "R_");
			sampleCode = IJ.getString("Name the current RSOM image stack with time and date Code(e.g.: R_154837_20180312_PEN3)", "R_");
		}
		
		if (imp.getShortTitle().equals(sampleCode)){ 
			RSOMImg = imp;
		} else {
			FileSaver fs = new FileSaver(imp);
			filePath = dirPath + sampleCode + ".tif";
			fs.saveAsTiffStack(filePath);
			//imp.close();
			IJ.run("Collect Garbage", "");
			System.gc();
			RSOMImg = IJ.openImage(filePath);
		}
		
		// image sequence recognized as no file with name found
		/*
		File imgF = new File(filePath);
		if (imgF.exists()) {
			isSequence = false;
		} else {
			isSequence = true;
			dirPath = new File(dirPath).getParent();
			if (!dirPath.endsWith(File.separator)) {
				dirPath += File.separator;
			}
		}
		if (isSequence) {
			FileSaver fs = new FileSaver(imp);
			filePath = dirPath + sampleCode + ".tif";
			fs.saveAsTiffStack(filePath);
			imp.close();
			IJ.run("Collect Garbage", "");
			System.gc();
			RSOMImg = IJ.openImage(filePath);
			isSequence = false;	// added on 2019/04/03 as no image sequence supposed to be processed in the future
		} else if (imp.getShortTitle().equals(sampleCode)) {
			RSOMImg = imp;
		} else {
			return;
		}
		*/
		
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
	
	public int checkResultDir () {
		resultDirExist = false;
		if (!dirPath.endsWith(File.separator)) {
			dirPath += File.separator;
		}
		//int execCount = 1;
		resultDir = dirPath + sampleCode + "_result";//(" +String.valueOf(execCount) + ")";
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
		/*
		while (resultF.exists()) {
			execCount++;
			resultDir = dirPath + sampleCode + "_result";//(" +String.valueOf(execCount) + ")";
			resultF = new File(resultDir);
		}
		*/
		return 0;
		//File resultF = new File(resultDir);
		/*
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
		*/
	}
	
	public boolean isImageSequence (File input) {	
		if (!input.isDirectory())
			return false;
		File[] tifFileList = input.listFiles(new FilenameFilter() {
			public boolean accept(File dir, String filename) {
				boolean ext1 = filename.endsWith(".tif");
				boolean ext2 = filename.endsWith(".tiff");
				return (ext1 || ext2);	
		}});
		
		if (tifFileList.length < 2) return false;
		
		int nameLength	= tifFileList[0].getName().length();
		int fileSize = (int) (tifFileList[0].length()/10000);
		if (fileSize > 6104) return false;	// single slice should not exceed 61.4MB
		
		for (int i=1; i<tifFileList.length; i++) {
			if (tifFileList[i].getName().length() != nameLength)
				return false;
			int fileSize2 = (int) (tifFileList[i].length()/10000);
			if (fileSize2 != fileSize)
				return false;
		}
		
		ImagePlus imp = IJ.openImage(tifFileList[0].getAbsolutePath());
		if (imp==null) return false;
        if (imp.isStack()) return false;
        if (imp.getImageStackSize()>1) return false;
        
        int width = imp.getWidth();
        int height = imp.getHeight();
        int bitDepth = imp.getBitDepth();

		int maxImageCheck = Math.max(tifFileList.length, 3);		
		for (int i=1; i<maxImageCheck; i++) {
			imp = IJ.openImage(tifFileList[i].getAbsolutePath());
			if (imp.getWidth() != width) return false;
	        if (imp.getHeight() != height) return false;
	        if (imp.getBitDepth() != bitDepth) return false;
		}
		
		return true;
	}
	
	public boolean isImageSequence2 (
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
			if (tifFileList.length < 50)
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
		//String x = "R_112322_20191129_STP3_mcCorr1";
		if (null == fileName) return null;
		
		String fileSep = Pattern.quote(System.getProperty("file.separator"));
		
		String[] parts = fileName.split(fileSep);
		
		//fileName = fileName.replace("\\", "/");
		//String[] parts  = fileName.split("/");
		
		Pattern p = Pattern.compile(pattern);
		
		for (String part : parts) {
			Matcher m = p.matcher(part);
			if (m.find()) {
				return part.substring(m.start(), m.end());
			}
		}
		/*
		int timeCodeBeginIdx = fileName.lastIndexOf("R_");
		if (timeCodeBeginIdx == -1) return null;

		int dateCodeBeginIdx = fileName.indexOf("201");
		if (dateCodeBeginIdx == -1) return null;
		
		String timeCode = fileName.substring(timeCodeBeginIdx+2,timeCodeBeginIdx+8);
		String dateCode = fileName.substring(dateCodeBeginIdx,dateCodeBeginIdx+8);
		*/
		
		//String codeName = parts[0]+"_"+parts[1]+"_"+parts[2]+"_"+parts[3];
		
		return null;
	}
	
	public String getRSOMSampleCode2 (
			String fileName
			) {
		if (null == fileName) return null;
		File file = new File(fileName);
		if (!file.exists()) return null;
		if (file.isFile()) {
			String name = file.getName();
			int dotIdx = name.lastIndexOf(".");
			if (dotIdx<0) return name;
			String name2 = name.substring(0, dotIdx);
			return name2;
		}
		if (file.isDirectory()) {	// add parent dir and image sequence dir together by _
			String name = file.getName();
			String parentName = file.getParent();
			String code = getRSOMSampleCode(parentName);
			if (code==null) code="";
			return (code+"_"+name);
		}
		return null;
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
