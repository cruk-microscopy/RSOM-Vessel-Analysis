package uk.ac.cam.cruk.RSOM;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Font;
import java.awt.Point;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.prefs.Preferences;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JProgressBar;
import javax.swing.border.Border;

import java.util.ArrayList;
import java.util.Arrays;

import org.apache.commons.io.FilenameUtils;
import org.scijava.prefs.DefaultPrefService;

import fiji.util.gui.GenericDialogPlus;

import ij.gui.HistogramWindow;
import ij.gui.ImageCanvas;
import ij.gui.Roi;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.io.FileSaver;
import ij.io.SaveDialog;
import ij.measure.ResultsTable;
import ij.plugin.FolderOpener;
import ij.plugin.Histogram;
import ij.plugin.JpegWriter;
import ij.plugin.PlugIn;
import ij.plugin.ScreenGrabber;
import ij.plugin.frame.RoiManager;
import ij.process.ImageStatistics;
import ij.process.StackStatistics;
import ij.WindowManager;

public class CheckAndSaveResult implements PlugIn{

	public RsomImageStack ris;
	public static String imgPath;
	protected static Boolean useCalibration = false;
	protected static double voxelSizeX = 20;
	protected static double voxelSizeY = 20;
	protected static double voxelSizeZ = 4;
	protected static String calUnit = "micron";
	public static Boolean getSlc = true;
	public static Boolean getRoi = true;
	public static Boolean getObj = true;
	public static int connIdx = 0;
	public static int connectivity = 26;
	public static Boolean getHst = true;
	public static double histMin = 20;
	public static double histMax = 260;
	public static int nBins = 10;
	
	public static boolean saveAsTiffStack (
			ImagePlus imp,
			String savePath
			) {
		FileSaver fs = new FileSaver(imp);
		return fs.saveAsTiffStack(savePath);
	}
	
	public static boolean saveMask (
			ImagePlus maskImage,
			String savePath
			) {
		FileSaver fs = new FileSaver(maskImage);
		fs.saveAsZip(savePath);
		return true;
	}
	
	public static boolean saveROI (
			RoiManager rm,
			String savePath
			) {
		//FileSaver fs = new FileSaver(maskImage);
		//fs.saveAsZip(savePath);
		rm.runCommand("Deselect");
		rm.runCommand("Save", savePath);
		return true;
	}
	
	public static boolean saveObjMap (
			ImagePlus objImage,
			String savePath
			) {
		FileSaver fs = new FileSaver(objImage);
		fs.saveAsTiffStack(savePath);
		return true;
	}
	
	public static boolean saveDiameterMap (
			ImagePlus diamImage,
			String savePath
			) {
		FileSaver fs = new FileSaver(diamImage);
		fs.saveAsTiffStack(savePath);
		return true;
	}
	
	public static boolean saveSliceWiseResult (
			ImagePlus ori,	// original RSOM image stack
			ImagePlus mask,	// binary mask image stack
			double[] voxelSize,
			String voxelUnit,
			RoiManager rm,	// RoiManager contains the 3D selection
			String resultSavingPath,	// path to save result_slice-wise.csv
			Boolean overwrite,	//	overwrite if file already exist
			Boolean append	// append to the end if file already exist
			) {
		
		ori.deleteRoi();	mask.deleteRoi();
		int[] dim = ori.getDimensions();
		int x = dim[0]; int y = dim[1]; int z = dim[3];
		int c = dim[2]; int t = dim[4];
		
		if (rm == null) return false;
		int nROI = rm.getCount();
		if (nROI == 0) return false;
		
		int[] col_slice = new int[nROI];
		int[] col_area = new int[nROI];
		int[] col_vox = new int[nROI];
		double[] col_areafrac = new double[nROI];
		double[] col_intensityMean = new double[nROI];
		double[] col_intensitySum = new double[nROI];
		double totalROIArea = 0;	double totalThresVox = 0;
		double totalIntensity = 0;
		
		
		
		ImageStatistics stats_mask; ImageStatistics stats_ori;
		for (int i=0; i < nROI; i++) {
			rm.select(mask,i);	stats_mask = mask.getRawStatistics();
			col_slice[i] = mask.getZ();
			rm.select(ori,i);	stats_ori = ori.getRawStatistics();
			col_area[i] = (int) stats_mask.area;
			col_vox[i] = (int) (stats_mask.area * stats_mask.mean / 255);
			col_intensityMean[i] = stats_ori.mean;
			col_intensitySum[i] = stats_ori.area * stats_ori.mean;
			col_areafrac[i] = (double)col_vox[i] / (double)col_area[i] * 100;
			totalROIArea += col_area[i];
	 		totalThresVox += col_vox[i];
	 		totalIntensity += col_intensitySum[i];
		}
		
		ResultsTable rt = new ResultsTable();
		rt.setPrecision(3);
		// !!!correct isotropicity in the future
		double pxSize = voxelSize[0]*voxelSize[1];
		double vxSize = pxSize * voxelSize[2];
		double isoPxSize = Math.sqrt(voxelSize[0]*voxelSize[1]);
		double isoVxSize = Math.cbrt(voxelSize[0]*voxelSize[1]*voxelSize[2]);
				
		for (int i = 0; i < nROI; i++) {
			rt.incrementCounter();
			rt.addValue("index", i+1);
			rt.addValue("slice number", col_slice[i]);
			rt.addValue("selection area(µm^2)", col_area[i] * pxSize);
			rt.addValue("thresholded voxel count", col_vox[i]);
			rt.addValue("thresholded area fraction(%)", col_areafrac[i]);
			rt.addValue("mean signal intensity", col_intensityMean[i]);
			rt.addValue("sum signal intensity", col_intensitySum[i]);
		}
		
		// construct summary information
		String summaryString = '\n' + "Summary:\n";
		String calString = ",voxel size:," + String.valueOf(voxelSize[0]) + "*"
					+ String.valueOf(voxelSize[1]) + "*" + String.valueOf(voxelSize[2])
					+ "," + voxelUnit + "^3" + '\n'
					+ ",isotropical pixel size:," + String.valueOf(isoPxSize)
					+ "," + voxelUnit + '\n'
					+ ",isotropical voxel size:," + String.valueOf(isoVxSize)
					+ "," + voxelUnit + '\n'
					+ '\n';
		String stackString = ",stack voxel number:," + String.valueOf(x*y*z) + '\n'
					+ ",stack volume:," 
					+ String.valueOf(x*y*z*vxSize) + "," + voxelUnit + "^3\n"
					+ '\n';
		String roiString = ",selection voxel number:," + String.valueOf(totalROIArea) + '\n'
					+ ",selection 3D volume:," 
					+ String.valueOf(totalROIArea*vxSize) + "," + voxelUnit + "^3\n"
					+ '\n';
		String vxString = ",thresholded voxel number:," + String.valueOf(totalThresVox) + '\n'
					+ ",thresholded voxel volume:," 
					+ String.valueOf(totalThresVox*vxSize) + "," + voxelUnit + "^3\n"
					+ '\n';
		String fracString = ",selection fraction:,"	+ String.valueOf(totalROIArea/x/y/z*100)
					+ ",%" + '\n'
					+ ",thresholded voxel fraction:,"
					+ String.valueOf(totalThresVox/x/y/z*100)
					+ ",%" + '\n'
					+ ",thresholded voxel to selection fraction:,"
					+ String.valueOf(totalThresVox/totalROIArea*100)
					+ ",%" + '\n'
					+ '\n';
		String intenString = ",Mean signal intensity per pixel (without background):,"
					+ String.valueOf(totalIntensity/totalThresVox) + '\n'
					+ ",Mean signal intensity per cubic " + voxelUnit + " (without background):,"
					+ String.valueOf(totalIntensity/totalThresVox/vxSize) + '\n'
					+ ",Mean signal intensity per slice (within selection range including background):,"
					+ String.valueOf(totalIntensity/nROI)
					+ '\n';
		try {
			rt.saveAs(resultSavingPath);
			IJ.append(
					summaryString+
					calString+
					stackString+
					roiString+
					vxString+
					fracString+
					intenString,
					resultSavingPath);
			return true;
		} catch (IOException e) {
			e.printStackTrace();
			IJ.log("Slice-wise result saving error!");
			return false;
		}
		
	}
	
	
	public static Boolean saveRoiWiseResult(
			ImagePlus ori,
			ImagePlus mask,
			ImagePlus objMap,
			ImagePlus diaMap,
			double[] voxelSize,
			String voxelUnit,
			RoiManager rm,	// from connected-component
			String resultSavingPath,
			Boolean overwrite,
			Boolean append
			) {
		
		if (ori == null || objMap == null || diaMap == null) return false;
		if (rm == null) return false;
		else if (rm.getCount() == 0) return false;
		int nROI = rm.getCount();
		
		objMap.setActivated();
		objMap.deleteRoi();	mask.deleteRoi();
		int[] dim = objMap.getDimensions();
		//int x = dim[0]; int y = dim[1]; int z = dim[3];
		//int c = dim[2]; int t = dim[4];

		String[] col_RoiID = new String[nROI];
		int[] col_area = new int[nROI];
		int[] col_sliceID = new int[nROI];
		int[] col_objID = new int[nROI];
		double[] col_diaMean = new double[nROI];
		double[] col_diaMin = new double[nROI];
		double[] col_diaMax = new double[nROI];
		//double[] col_dia_histMin = new double[nROI];
		//double[] col_dia_histMax = new double[nROI];
		double[] col_intensityMean = new double[nROI];
		double[] col_intensitySum = new double[nROI];
		double totalROIArea = 0;
		double totalROIDiameter = 0;
		double totalIntensity = 0;
		int minRoiAreaIdx = 0;	int maxRoiAreaIdx = 0;
		int minRoiDiameterIdx = 0;	int maxRoiDiameterIdx = 0;
		int minIntensityIdx = 0;	int maxnIntensityIdx = 0;
		
		ImageStatistics stats_ori; ImageStatistics stats_msk;
		ImageStatistics stats_obj; ImageStatistics stats_dia;
		int currentSlise = 0;
		
		for (int i=0; i < nROI; i++) {
			rm.select(objMap,i); stats_obj = objMap.getRawStatistics();
			currentSlise = objMap.getZ();
			ori.setZ(currentSlise); mask.setZ(currentSlise); diaMap.setZ(currentSlise);
			rm.select(ori,i); stats_ori = ori.getRawStatistics();
			rm.select(mask,i); stats_msk = mask.getRawStatistics();
			rm.select(diaMap,i); stats_dia = diaMap.getRawStatistics();	
			col_RoiID[i] = rm.getName(i);
			col_area[i] = (int) (stats_msk.area * stats_msk.mean / 255);
			col_sliceID[i] = currentSlise;
			col_objID[i] = (int) stats_obj.max;
			col_diaMean[i] = stats_dia.mean;
			col_diaMin[i] = stats_dia.min;
			col_diaMax[i] = stats_dia.max;
			//col_dia_histMin[i] = stats_dia.histMin;
			//col_dia_histMax[i] = stats_dia.histMax;
			col_intensityMean[i] = stats_ori.mean;
			col_intensitySum[i] = stats_ori.area * stats_ori.mean;
			totalROIArea += col_area[i];
			totalROIDiameter += col_diaMean[i];
			totalIntensity += col_intensityMean[i];
			
			minRoiAreaIdx = col_area[i] < col_area[minRoiAreaIdx] ? i : minRoiAreaIdx;
			maxRoiAreaIdx = col_area[i] > col_area[maxRoiAreaIdx] ? i : maxRoiAreaIdx;
			minRoiDiameterIdx = col_diaMin[i] < col_diaMin[minRoiDiameterIdx] ? i : minRoiDiameterIdx;
			maxRoiDiameterIdx = col_diaMax[i] > col_diaMax[maxRoiDiameterIdx] ? i : maxRoiDiameterIdx;
			minIntensityIdx = col_intensityMean[i] < col_intensityMean[minIntensityIdx] ? i : minIntensityIdx;
			maxnIntensityIdx = col_intensityMean[i] > col_intensityMean[maxnIntensityIdx] ? i : maxnIntensityIdx;
		}
		
		ResultsTable rt = new ResultsTable();
		rt.setPrecision(3);
		double pxSize = voxelSize[0]*voxelSize[1];
		double vxSize = pxSize*voxelSize[2];
		// !!! get isotropical calibration in the future;
		//double isoVxSize = Math.cbrt(vxSize);
		double isoPxSize = Math.sqrt(pxSize);
		//double isoVxSize = Math.cbrt(vxSize);
		double isoVxSize = isoPxSize;
		
		for (int i = 0; i < nROI; i++) {
			rt.incrementCounter();
			rt.addValue("index", i+1);
			rt.addValue("ROI-ID", col_RoiID[i]);
			rt.addValue("area(µm^2)", col_area[i] * pxSize);
			rt.addValue("slice number", col_sliceID[i]);
			rt.addValue("object-ID", col_objID[i]);
			rt.addValue("diameter mean(µm)", col_diaMean[i]);
			rt.addValue("diameter min(µm)", col_diaMin[i]);
			rt.addValue("diameter max(µm)", col_diaMax[i]);
			rt.addValue("mean signal intensity", col_intensityMean[i]);
			rt.addValue("sum signal intensity", col_intensitySum[i]);
		}
		
		// construct summary information
		String summaryString = '\n' + "Summary:\n";
		String calString = ",voxel size:," + String.valueOf(voxelSize[0]) + "*"
					+ String.valueOf(voxelSize[1]) + "*" + String.valueOf(voxelSize[2])
					+ "," + voxelUnit + "^3" + '\n'
					+ ",isotropical pixel size:," + String.valueOf(isoPxSize)
					+ "," + voxelUnit + '\n'
					+ ",isotropical voxel size:," + String.valueOf(isoVxSize)
					+ "," + voxelUnit + '\n'
					+ '\n';
		String areaString = ",average 2D ROI size:," + String.valueOf(totalROIArea/nROI*pxSize)
					+ "," + voxelUnit + "^2" + '\n'
					+ ",minimum average 2D ROI size:," + String.valueOf(col_area[minRoiAreaIdx]*pxSize)
					+ "," + voxelUnit + "^2, ROI-ID:," + String.valueOf(minRoiAreaIdx + 1) + '\n'
					+ ",maximum average 2D ROI size:," + String.valueOf(col_area[maxRoiAreaIdx]*pxSize)
					+ "," + voxelUnit + "^2, ROI-ID:," + String.valueOf(maxRoiAreaIdx + 1) + '\n'
					+ '\n';
		String diaString = ",average 2D ROI diameter:," + String.valueOf(totalROIDiameter/nROI) 
					+ "," + voxelUnit + '\n'
					+ ",minimum 2D ROI diameter:," + String.valueOf(col_diaMin[minRoiDiameterIdx])
					+ "," + voxelUnit + ", ROI-ID:," + String.valueOf(minRoiDiameterIdx + 1) + '\n'
					+ ",maximum 2D ROI diameter:," + String.valueOf(col_diaMax[maxRoiDiameterIdx])
					+ "," + voxelUnit + ", ROI-ID:," + String.valueOf(maxRoiDiameterIdx + 1) + '\n'
					+ '\n';
		String intensityString = ",average signal intensity per ROI:," + String.valueOf(totalIntensity/nROI) 
					+ '\n'
					+ ",minimum average signal intensity of ROI:," + String.valueOf(col_intensityMean[minIntensityIdx])
					+ ", , ROI-ID:," + String.valueOf(minIntensityIdx + 1) + '\n'
					+ ",maximum average signal intensity of ROI:," + String.valueOf(col_intensityMean[maxnIntensityIdx])
					+ ", , ROI-ID:," + String.valueOf(maxnIntensityIdx + 1) + '\n'
					+ '\n';
		try {
			rt.saveAs(resultSavingPath);
			IJ.append(
					summaryString+
					calString+
					areaString+
					diaString+
					intensityString,
					resultSavingPath);
			return true;
		} catch (IOException e) {
			e.printStackTrace();
			IJ.log("ROI-wise result saving error!");
			return false;
		}
		
	}
	
	
	public static Boolean saveObjWiseResult( // !!! note object ID could be non-continuous !!!
			ImagePlus ori,
			ImagePlus mask,
			ImagePlus objMap,
			int connectivity,
			ImagePlus diaMap,
			double[] voxelSize,
			String voxelUnit,
			RoiManager rm,	// from connected-component
			String resultSavingPath,
			Boolean overwrite,
			Boolean append
			) {
		
		if (ori == null || objMap == null || diaMap == null) return false;
		if (!objMap.isStack()) return false;
		if (rm == null) return false;
		else if (rm.getCount() == 0) return false;
		
		int nROI = rm.getCount();
		objMap.setActivated();
		objMap.deleteRoi(); mask.deleteRoi();
		int[] dim = objMap.getDimensions();
		//int x = dim[0]; int y = dim[1]; int z = dim[3];
		//int c = dim[2]; int t = dim[4];
		StackStatistics stats = new StackStatistics(objMap);
		int nObj = (int)stats.max;
		
		int[] col_objID = new int[nROI];
		double[] col_vol = new double[nROI];
		double[] col_diaSum = new double[nROI];
		double[] col_diaMin = new double[nROI];
		double[] col_diaMax = new double[nROI];
		double[] col_intensitySum = new double[nROI];
		double[] col_intensityMin = new double[nROI];
		double[] col_intensityMax = new double[nROI];
		
		double[] col_ObjVol = new double[nObj];
		double[] col_ObjDiaSum = new double[nObj];
		double[] col_ObjDiaMin = new double[nObj];
		double[] col_ObjDiaMax = new double[nObj];
		double[] col_ObjIntSum = new double[nObj];
		double[] col_ObjIntMin = new double[nObj];
		double[] col_ObjIntMax = new double[nObj];
		
		ImageStatistics stats_ori; ImageStatistics stats_msk;
		ImageStatistics stats_obj; ImageStatistics stats_dia;
		
		int currentSlise = 0;	int currentObjIdx = 0;
		for (int i=0; i < nROI; i++) {
			rm.select(objMap,i);	stats_obj = objMap.getRawStatistics();		
			currentSlise = objMap.getZ();		
			ori.setZ(currentSlise); mask.setZ(currentSlise); diaMap.setZ(currentSlise);
			rm.select(ori,i);	stats_ori = ori.getRawStatistics();
			rm.select(mask,i);	stats_msk = mask.getRawStatistics();
			rm.select(diaMap,i);	stats_dia = diaMap.getRawStatistics();

			col_objID[i] = (int)stats_obj.max;	//account for ROI holes
			col_vol[i] = (int) (stats_msk.area * stats_msk.mean / 255);
			col_diaSum[i] = stats_dia.area * stats_dia.mean;
			col_diaMin[i] = stats_dia.min;
			col_diaMax[i] = stats_dia.max;
			col_intensitySum[i] = stats_ori.area * stats_ori.mean;
			col_intensityMin[i] = stats_ori.min;
			col_intensityMax[i] = stats_ori.max;
			
			currentObjIdx = col_objID[i] - 1;
			col_ObjVol[currentObjIdx] += col_vol[i];
			col_ObjDiaSum[currentObjIdx] += col_diaSum[i];
			// Initialise object minimum diameter with 1st match entry
			if (col_ObjDiaMin[currentObjIdx] == 0)	col_ObjDiaMin[currentObjIdx] = col_diaMin[i];
			col_ObjDiaMin[currentObjIdx] = 
					Math.min(col_diaMin[i], col_ObjDiaMin[currentObjIdx]);		
			// Initialise object maximum diameter with 1st match entry
			if (col_ObjDiaMax[currentObjIdx] == 0)	col_ObjDiaMax[currentObjIdx] = col_diaMax[i];
			col_ObjDiaMax[currentObjIdx] = 
					Math.max(col_diaMax[i], col_ObjDiaMax[currentObjIdx]);		
					
			col_ObjIntSum[currentObjIdx] += col_intensitySum[i];
			// Initialise object minimum intensity with 1st match entry
			if (col_ObjIntMin[currentObjIdx] == 0)	col_ObjIntMin[currentObjIdx] = col_intensityMin[i];
			col_ObjIntMin[currentObjIdx] = 
					Math.min(col_intensityMin[i], col_intensityMin[currentObjIdx]);		
			// Initialise object minimum intensity with 1st match entry
			if (col_ObjIntMax[currentObjIdx] == 0)	col_ObjIntMax[currentObjIdx] = col_intensityMax[i];
			col_ObjIntMax[currentObjIdx] = 
					Math.max(col_intensityMax[i], col_intensityMax[currentObjIdx]);
		}
		
		ResultsTable rt = new ResultsTable();
		rt.setPrecision(3);
		double pxSize = voxelSize[0]*voxelSize[1];
		double vxSize = pxSize*voxelSize[2];
		// !!! get isotropical calibration in the future;
		//double isoVxSize = Math.cbrt(vxSize);
		double isoPxSize = Math.sqrt(pxSize);
		//double isoVxSize = Math.cbrt(vxSize);
		double isoVxSize = isoPxSize;
		
		double totalObjVol = 0;
		double totalObjDia = 0;
		double totalObjInt = 0;
		int minObjVolIdx = 0;	int maxObjVolIdx = 0;
		int minObjDiaIdx = 0;	int maxObjDiaIdx = 0;
		int minObjIntIdx = 0;	int maxObjIntIdx = 0;
		
		int numEmptyObject = 0;
		
		for (int i = 0; i < nObj; i++) {
			if (col_ObjVol[i] == 0) {
				numEmptyObject += 1;
				continue; // skip empty object
			}
			rt.incrementCounter();
			rt.addValue("index", i+1-numEmptyObject);
			rt.addValue("Obj-ID", i+1);
			rt.addValue("volume(µm^3)", col_ObjVol[i] * vxSize);
			//rt.addValue("diameter mean(µm)", col_ObjDiaSum[i] / col_ObjVol[i] * isoVxSize);
			rt.addValue("diameter mean(µm)", col_ObjDiaSum[i] / col_ObjVol[i]);
			rt.addValue("diameter min(µm)", col_ObjDiaMin[i]);
			rt.addValue("diameter max(µm)", col_ObjDiaMax[i]);
			rt.addValue("signal intensity mean", col_ObjIntSum[i] / col_ObjVol[i]);
			rt.addValue("signal intensity min", col_ObjIntMin[i]);
			rt.addValue("signal intensity max", col_ObjIntMax[i]);
			
			totalObjVol += col_ObjVol[i] * vxSize;
			totalObjDia += col_ObjDiaSum[i] / col_ObjVol[i];
			totalObjInt += col_ObjIntSum[i] / col_ObjVol[i];
			
			minObjVolIdx = col_ObjVol[i] < col_ObjVol[minObjVolIdx] ? i : minObjVolIdx;
			maxObjVolIdx = col_ObjVol[i] > col_ObjVol[maxObjVolIdx] ? i : maxObjVolIdx;
			minObjDiaIdx = col_ObjDiaMin[i] < col_ObjDiaMin[minObjDiaIdx] ? i : minObjDiaIdx;
			maxObjDiaIdx = col_ObjDiaMax[i] > col_ObjDiaMax[maxObjDiaIdx] ? i : maxObjDiaIdx;
			minObjIntIdx = col_ObjIntMin[i] < col_ObjIntMin[minObjIntIdx] ? i : minObjIntIdx;
			maxObjIntIdx = col_ObjIntMax[i] > col_ObjIntMax[maxObjIntIdx] ? i : maxObjIntIdx;
		}
		
		nObj -= numEmptyObject; // update number of object to remove empty object(s)
		// construct summary information
		String summaryString = '\n' + "Summary:\n";
		String calString = ",voxel size:," + String.valueOf(voxelSize[0]) + "*"
					+ String.valueOf(voxelSize[1]) + "*" + String.valueOf(voxelSize[2])
					+ "," + voxelUnit + "^3" + '\n'
					+ ",isotropical pixel size:," + String.valueOf(isoPxSize)
					+ "," + voxelUnit + '\n'
					+ ",isotropical voxel size:," + String.valueOf(isoVxSize)
					+ "," + voxelUnit + '\n'
					+ '\n';
		String objString = ",3D objects are recognized as connected regions with connectivity:," 
					+ String.valueOf(connectivity) + '\n'
					+ ",Total 3D object count:," + String.valueOf(nObj) + '\n'
					+ '\n';
		String volString = ",Average object volume:," + String.valueOf(totalObjVol / nObj)
					+ "," + voxelUnit + "^3" + '\n'
					+ ",Minimum volume of object:," + String.valueOf(col_ObjVol[minObjVolIdx] * vxSize)
					+ "," + voxelUnit + "^3, Obj-ID:," + String.valueOf(minObjVolIdx + 1) + '\n'
					+ ",Maximum volume of object:," + String.valueOf(col_ObjVol[maxObjVolIdx] * vxSize)
					+ "," + voxelUnit + "^3, Obj-ID:," + String.valueOf(maxObjVolIdx + 1) + '\n'
					+ '\n';
		String diaString = ",Average object diameter:," + String.valueOf(totalObjDia / nObj)
					+ "," + voxelUnit + '\n'
					+ ",Minimum diameter found in object:," + String.valueOf(col_ObjDiaMin[minObjDiaIdx])
					+ "," + voxelUnit + ", Obj-ID:," + String.valueOf(minObjDiaIdx + 1) + '\n'
					+ ",Maximum diameter found in object:," + String.valueOf(col_ObjDiaMax[maxObjDiaIdx])
					+ "," + voxelUnit + ", Obj-ID:," + String.valueOf(maxObjDiaIdx + 1) + '\n'
					+ '\n';
		String intensityString = ",Average object intensity:," + String.valueOf(totalObjInt / nObj) + '\n' 
					+ ",Minimum signal intensity found in object:,"
					+ String.valueOf(col_ObjIntMin[minObjIntIdx]) + ", , Obj-ID:,"
					+ String.valueOf(minObjIntIdx + 1) + '\n'
					+ ",Maximum signal intensity found in object:,"
					+ String.valueOf(col_ObjIntMax[maxObjIntIdx]) + ", , Obj-ID:,"
					+ String.valueOf(maxObjIntIdx + 1) + '\n'
					+ '\n';
		try {
			rt.saveAs(resultSavingPath);
			IJ.append(
					summaryString+
					calString+
					objString+
					volString+
					diaString+
					intensityString,
					resultSavingPath);
			return true;
		} catch (IOException e) {
			e.printStackTrace();
			IJ.log("Object-wise result saving error!");
			return false;
		}
		
	}

	
	public static Boolean saveHisto(
			ImagePlus diaMap,
			double min,
			double max,
			int nBins,
			String resultSavingPath,
			Boolean overwrite,
			Boolean append
			) {
		diaMap.setActivated();
		StackStatistics stats = new StackStatistics(diaMap);
		if (max != 0) {
			stats = new StackStatistics(diaMap, nBins, min, max);
		} else {
			final double binSize = (stats.max - stats.min) / nBins;
			stats = new StackStatistics(diaMap, nBins, stats.min+binSize, stats.max+binSize);
		}
		HistogramWindow h = new HistogramWindow("histogram of diameter map", diaMap, stats);

		IJ.saveAs(h.getImagePlus(), "PNG", resultSavingPath);
		h.close();
		return true;
	}
	
	
	public static Boolean saveLog(
			ParameterDialog pd,
			RsomImageStack ris,
			double[] bounds,
			int connectivity,
			String date,
			String time,
			String duration,
			String resultSavingPath
			) throws IOException {
		
		int runtimeCount = 0;
		File csvFile = new File(resultSavingPath);
		
		if (!csvFile.exists()) {
			try {
		    	csvFile.createNewFile();
		    } catch (IOException e) {
		      e.printStackTrace();
		      IJ.log("Can not create log file!");
		      return false;
		    }
		} else {
			try {
				runtimeCount = Integer.parseInt(searchLogForEntry(resultSavingPath, "Execution count:", 1));
		    }  catch (NumberFormatException e) {
		    	IJ.error("Runtime Count Number Format Exception!" + '\n' + String.valueOf(runtimeCount));
		    }
		}
		runtimeCount += 1;
		
		// construct summary information
		String titleString;
		if (pd == null) {
			titleString = "from \"Get Analysis result from processed images\"" + '\n';
		} else {
			titleString = "from \"Vessel diameter analysis\"" + '\n';
		}
		titleString = titleString.concat("Execution count:," + String.valueOf(runtimeCount) + '\n');
		
		String timeString = date + '\n' + time + '\n'
					+ "plugin runtime:, " + duration
					+ '\n';

		String inputImageString = "input image stack:," + ris.imageName + '\n'
					+ "file path:," + ris.filePath + '\n'
					+ "stack is image sequence:," + (ris.isSequence?"YES":"NO") + '\n'
					+ "stack is transposed:," + (ris.isTransposed?"YES":"NO") 
					+ '\n';
		String inputParamstring = null;
		if (pd == null) {
			inputParamstring = "background subtraction radius:," + 
					searchLogForEntry(resultSavingPath, "background subtraction radius:", 1) + '\n'
					+ "thresholding method:," + 
					searchLogForEntry(resultSavingPath, "thresholding method:", 1) + '\n'
					+ "thresholding bounds:," + 
					searchLogForEntry(resultSavingPath, "thresholding bounds:", 1) + ',' +
					searchLogForEntry(resultSavingPath, "thresholding bounds:", 2) + '\n'
					+ "median filter raidus:," + 
					searchLogForEntry(resultSavingPath, "median filter raidus:", 1) + '\n'
					+ "selected area (ROI) instead of whole stack:," + 
					searchLogForEntry(resultSavingPath, "selected area (ROI) instead of whole stack:", 1) + '\n'
					+ "3D object connectivity:," + String.valueOf(connectivity) + '\n'
					+ '\n';
		} else {
			double bgRaduis = pd.doSubtractBackground?pd.bgRadius:0;
			inputParamstring = "background subtraction radius:," + String.valueOf(bgRaduis) + '\n'
						+ "thresholding method:," + (pd.autoOrManualThreshold==1?"Manual":pd.autoMethod) + '\n'
						+ "thresholding bounds:," + String.valueOf(bounds[0]) + "," + String.valueOf(bounds[1]) + '\n'
						+ "median filter raidus:," + String.valueOf(pd.mdFtRadius) + '\n'
						+ "selected area (ROI) instead of whole stack:," + (pd.doRoi?"YES":"NO") + '\n'
						+ "3D object connectivity:," + String.valueOf(connectivity) + '\n'
						+ '\n';
		}
		if (IJ.append(
				titleString+
				timeString+
				inputImageString+
				inputParamstring,
				resultSavingPath
				) != null) {
			IJ.log("Can not append current plugin operation information to log file!");
			return false;
		}
		return true;

	}
	
	public static void makeResultPaths (
			RsomImageStack ris
			) {
		if (!resultDirExist(ris)) makeDir(ris);
		String resultDir = ris.resultDir;
		String imageName = ris.nameValid(ris.imageName);
		
		ris.executionLogPath =resultDir + File.separator + "execution_log.csv";
		ris.maskPath = resultDir + File.separator + imageName + "_mask.zip";
		ris.objectMapPath = resultDir + File.separator + imageName + "_objectMap.tiff";
		ris.roiPath = resultDir + File.separator + imageName + "_Roiset.zip";
		ris.selectionPath = resultDir + File.separator + imageName + "_selection3D.zip";
		ris.diameterMapPath = resultDir + File.separator + imageName + "_diameterMap.tiff";
		ris.resultSlicePath = resultDir + File.separator + imageName + "_slice-wise.csv";
		ris.resultRoiPath = resultDir + File.separator + imageName + "_ROI-wise.csv";
		ris.resultObjectPath = resultDir + File.separator + imageName + "_object-wise.csv";
		ris.resultHistogramPath = resultDir + File.separator + imageName + "_histogram.png";
	}
	
	
	public static void makeDir (
			RsomImageStack ris
			) {
		if (resultDirExist(ris)) return;
		
		if (!ris.imageOnDisk) {
			FileSaver fs = new FileSaver(ris.RSOMImg);
			if (!fs.saveAsTiff()) return;
			ris.dirPath = IJ.getDirectory("image");
			ris.imageTitle = ris.RSOMImg.getTitle();
			ris.imageName = ris.RSOMImg.getShortTitle();
		}
		
		String fileName = FilenameUtils.removeExtension(ris.imageTitle);
		ris.resultDir = ris.dirPath + File.separator + fileName + "_result";
		File f = new File(ris.resultDir);
		if (!f.exists()) {
            if(!f.mkdir()) {
            	IJ.error("File save error","Can not create result folder!");
            }
		}
		ris.resultDirExist = true;
		return;
	}
	
	public static boolean processed(
			RsomImageStack ris
			) {
		if (!resultDirExist(ris)) return false;
		File logFile = new File(ris.executionLogPath);
		if (!logFile.exists()) return false;
		return true;
	}
	
	public static boolean resultDirExist (
			String resultDirPath
			) {
		File resultDir = new File(resultDirPath);
		if (resultDir == null || !resultDir.isDirectory()) {
			IJ.log("result path invalid or does not exist.");
			return false;
		} else {
			return true;
		}
		
	}
	
	public static boolean fileExist(
			String filePath
			) {
		File f = new File(filePath);
		if (f == null || f.isDirectory()) {
			IJ.log("file path invalid or does not exist.");
			return false;
		} else {
			return true;
		}
	}
	
	public static boolean processed(
			String resultDirPath
			) {
		if (!resultDirExist(resultDirPath)) return false;
		String logFilePath = resultDirPath + File.separator + "execution_log.csv";
		File logFile = new File(logFilePath);
		if (!logFile.exists()) return false;
		return true;
	}
	
	public static boolean resultDirExist (
			RsomImageStack ris
			) {
		if (ris == null) return false;
		return ris.resultDirExist;
	}
	
	
	public static String searchLogForEntry(
			String csvFilePath,
			String searchString,
			int interval
			) throws IOException {
		
		String foundValue = null;
		
		String[] csvText = readCsvFileToArray(csvFilePath);
		if (csvText == null) {
			return foundValue;
		} else {
			try {
				found:
				for (int row=csvText.length-1; row>0; row--) {
					String[] values = csvText[row].split(",");
					for (int cell=0; cell< values.length; cell++) {
						if (values[cell].toLowerCase().contains(searchString.toLowerCase())) {
							foundValue = values[cell+interval];
							break found;
						}
					}
				}
			} catch (ArrayIndexOutOfBoundsException exception) {
				foundValue = null;
			}
			return foundValue;
		}
	}
	
	// not working in ReadAndWriteCsv class
	public static String[] readCsvFileToArray (
			String csvFilePath
			) {
        String row = "";
        ArrayList<String> ar = new ArrayList<String>();
        //String[] output = null;
        BufferedReader br = null;
        try {
        	br = new BufferedReader(new FileReader(csvFilePath));
            while ((row = br.readLine()) != null) {
                // use comma as separator 		
                ar.add(row);
            }
            br.close();
            String[] output = new String[ar.size()];
            ar.toArray(output);
            return output;
        } catch (IOException e) {
            e.printStackTrace();
        }
		return null;
	}
	
	// correct 0-diameter caused by ROI-hole
	public static void correctRoiHole() {
		
	}
	
	@Override
	public void run(String arg0) {
		
		// get start date and time
		long startTime = GetDateAndTime.getCurrentTimeInMs();
		String date = GetDateAndTime.getCurrentDate();
		String time = GetDateAndTime.getCurrentTime();
				
		// initiate and display the progress bar
			ImageJ j = IJ.getInstance();
		    JFrame f = new JFrame("RSOM analysis progress");
		    f.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		    Container content = f.getContentPane();
		    JProgressBar progressBar = new JProgressBar(0,100);
		    f.setLocation(j.getX()+j.getWidth()+10,j.getY()+5);
		    Border border = BorderFactory.createTitledBorder("Getting user input...");
		    progressBar.setBorder(border);
		    content.add(progressBar, BorderLayout.NORTH);
		    f.setSize(400, 100);
		    f.setVisible(true);
		    progressBar.setStringPainted(true);
		    progressBar.setValue(0);
		
		if(!addDialog()) {
			f.dispose();
			return;
		}
		// update progress bar
			border = BorderFactory.createTitledBorder("Loading image...");
		    progressBar.setBorder(border);
			progressBar.setValue(progressBar.getValue()+10);
		if (!resultDirExist(ris)) {
			f.dispose();
			return;
		}
		ImagePlus ori = ris.RSOMImg;
		
		// check if necessary to re-compute the object map
		Boolean newObjMap = false; ImagePlus mask = null; ImagePlus dia = null;
		if (getRoi || getObj || getHst) {
			int preConnectivity = 0;
			try {
				preConnectivity = Integer.parseInt(searchLogForEntry(ris.executionLogPath, "3D object connectivity:", 1));
			} catch (IOException e1) {
				e1.printStackTrace();
			}
			if (preConnectivity != connectivity) {
				newObjMap = true;
				// update object map
				border = BorderFactory.createTitledBorder("Update object map with connectivity " + String.valueOf(connectivity) + "...");
			    progressBar.setBorder(border);
				progressBar.setValue(progressBar.getValue()+10);
				mask = IJ.openImage(ris.maskPath);
				mask.deleteRoi();
				ConnectComponentAnalysis cc = new ConnectComponentAnalysis(
						mask,
						connectivity,
						16
						);
				ImagePlus objMap = cc.CC_3D(mask, connectivity, 16, ris.imageName);
				objMap.setCalibration(ris.RSOMImg.getCalibration());
				CheckAndSaveResult.saveObjMap(objMap, ris.objectMapPath);
				//rm.reset(); rm.close();
				ris.RSOMImg.deleteRoi();
			}
			// update progress bar
				border = BorderFactory.createTitledBorder("Loading diameter map...");
			    progressBar.setBorder(border);
				progressBar.setValue(progressBar.getValue()+10);
			dia = IJ.openImage(ris.diameterMapPath);
			if (dia==null) {IJ.log("Diameter map does not exist!"); f.dispose(); return;}
		}
		
		ImagePlus msk = null;
		if (getSlc || getRoi || getObj) {
			// update progress bar
					border = BorderFactory.createTitledBorder("Loading mask...");
				    progressBar.setBorder(border);
					progressBar.setValue(progressBar.getValue()+10);
			if (newObjMap) {
				msk = mask;
			} else {
				msk = IJ.openImage(ris.maskPath);
			}
			if (msk==null) {IJ.log("Mask image does not exist!"); f.dispose(); return;}
		}
		
		ImagePlus obj = null;
		if (getRoi || getObj) {
			// update progress bar
				border = BorderFactory.createTitledBorder("Loading object map...");
			    progressBar.setBorder(border);
				progressBar.setValue(progressBar.getValue()+10);
			obj = IJ.openImage(ris.objectMapPath);
			if (obj==null) {IJ.log("Object map does not exist!"); f.dispose(); return;}
		}
		
		
		
		double[] voxelSize = new double[3];
		String voxelUnit = null;
		if (useCalibration) {
			voxelSize = ris.calibration;
			voxelUnit = ris.calUnit;
		} else {
			voxelSize = new double[] {voxelSizeX, voxelSizeY, voxelSizeZ};
			voxelUnit = calUnit;
		}
		// update progress bar
			border = BorderFactory.createTitledBorder("Parsing result saving path...");
		    progressBar.setBorder(border);
			progressBar.setValue(progressBar.getValue()+10);
		String rmSelectionPath = ris.selectionPath;
		String rmRoiPath = ris.roiPath;
		String resultSlicePath = ris.resultSlicePath;
		String resultRoiPath = ris.resultRoiPath;
		String resultObjectPath = ris.resultObjectPath;
		String resultHistPath = ris.resultHistogramPath;
		
		// prepare RoiManager
		Roi[] oriRois = null; Boolean rmHidden = false; Point rmLocation = null;
		if (RoiManagerUtility.isOpen() && !RoiManagerUtility.isEmpty()) {
			oriRois = RoiManagerUtility.managerToRoiArray();
			rmHidden = RoiManagerUtility.isHidden();
			rmLocation = RoiManagerUtility.getLocation();
		}
		RoiManagerUtility.resetManager();
		RoiManagerUtility.hideManager();
		RoiManager rm = RoiManager.getInstance2();
		
		// update progress bar
			border = BorderFactory.createTitledBorder("Saving slice-wise result ...");
		    progressBar.setBorder(border);
			progressBar.setValue(progressBar.getValue()+10);
		// get slice-wise result
		if (getSlc) {
			RoiManagerUtility.resetManager();
			rm.runCommand("Open", rmSelectionPath);
			if (!saveSliceWiseResult(
					ori, msk, voxelSize, voxelUnit, rm, resultSlicePath, true, false)) {
				IJ.log("Slice-wise result not saved.");
			}
			RoiManagerUtility.resetManager();
		}
		
		// update progress bar
			border = BorderFactory.createTitledBorder("Saving ROI-wise result ...");
		    progressBar.setBorder(border);
			progressBar.setValue(progressBar.getValue()+10);
		// get ROI-wise result
		if (getRoi) {
			RoiManagerUtility.resetManager();
			rm.runCommand("Open", rmRoiPath);
			if (!saveRoiWiseResult(
					ori, msk, obj, dia, voxelSize, voxelUnit, rm, resultRoiPath, true, false)) {
				IJ.log("ROI-wise result not saved.");
			}
			RoiManagerUtility.resetManager();
		}
		
		// update progress bar
			border = BorderFactory.createTitledBorder("Saving object-wise result ...");
		    progressBar.setBorder(border);
			progressBar.setValue(progressBar.getValue()+10);
		// get object-wise result
		if (getObj) {
			RoiManagerUtility.resetManager();
			rm.runCommand("Open", rmRoiPath);
			if (!saveObjWiseResult(
					ori, msk, obj, connectivity, dia, voxelSize, voxelUnit, rm, resultObjectPath, true, false)) {
				IJ.log("Object-wise result not saved.");
			}
			RoiManagerUtility.resetManager();
		}
		RoiManagerUtility.closeManager();
		
		// update progress bar
			border = BorderFactory.createTitledBorder("Saving histogram ...");
		    progressBar.setBorder(border);
			progressBar.setValue(progressBar.getValue()+10);
		// get diameter histogram
		if (getHst) {
			if (!saveHisto(
					dia, histMin, histMax, nBins, resultHistPath, true, false)) {
				IJ.log("diameter histogram not saved.");
			}
		}
		
		// update progress bar
			border = BorderFactory.createTitledBorder("Cleaning up ...");
		    progressBar.setBorder(border);
			progressBar.setValue(progressBar.getValue()+5);
		// clean up, report, revert changes, and return
		if (ori != null) {ori.flush(); ori.close();}
		if (msk != null) {msk.flush(); msk.close();} 
		if (obj != null) {obj.flush(); obj.close();}
		if (dia != null) {dia.flush(); dia.close();} 
		// return original RoiManager, modify exist=true, append = false (overwrite)
		// revert original display mode: hide or show
		RoiManagerUtility.roiArrayToManager(oriRois, true, false);
		RoiManagerUtility.setLocation(rmLocation);
		if (RoiManagerUtility.isOpen()) {
			if (rmHidden) RoiManagerUtility.hideManager();
			else RoiManagerUtility.showManager();
		}
		
		// update progress bar
			border = BorderFactory.createTitledBorder("Updating operation log ...");
		    progressBar.setBorder(border);
			progressBar.setValue(progressBar.getValue()+5);
		// save runtime log
		long endTime = GetDateAndTime.getCurrentTimeInMs();
		String duration = GetDateAndTime.getDuration(endTime - startTime);
		// create and save log; if log file exist, append in the end
		try {
			if (!CheckAndSaveResult.saveLog(
					null, 
					ris, 
					new double[] {0, 0},
					connectivity,
					date, 
					time, 
					duration, 
					ris.executionLogPath)) {
				IJ.log("Current operation has not been logged.");
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		
		// complete and close progress bar
			border = BorderFactory.createTitledBorder("Processing complete ...");
		    progressBar.setBorder(border);
			progressBar.setValue(100);
		f.dispose();
		IJ.run("Collect Garbage", "");
		IJ.log("Analysis and result saving complete.");
		System.gc(); // system garbage collector (release memory back to computer)
		return;	
	}
	
	public static void main() {
		if (IJ.versionLessThan("1.52f")) System.exit(0);
		// make use of scijava parameter persistence storage	
		DefaultPrefService prefs = new DefaultPrefService();
		imgPath = prefs.get(String.class, "persistedString", imgPath);
		useCalibration = prefs.getBoolean(Boolean.class, "persistedBoolean", useCalibration);
		voxelSizeX = prefs.getDouble(Double.class, "persistedDouble", voxelSizeX);
		voxelSizeY = prefs.getDouble(Double.class, "persistedDouble", voxelSizeY);
		voxelSizeZ = prefs.getDouble(Double.class, "persistedDouble", voxelSizeZ);
		calUnit = prefs.get(String.class, "persistedString", calUnit);
		getSlc = prefs.getBoolean(Boolean.class, "persistedBoolean", getSlc);
		getRoi = prefs.getBoolean(Boolean.class, "persistedBoolean", getRoi);
		getObj = prefs.getBoolean(Boolean.class, "persistedBoolean", getObj);
		connIdx = prefs.getInt(Integer.class, "persistedDouble", connIdx);
		getHst = prefs.getBoolean(Boolean.class, "persistedBoolean", getHst);
		histMin = prefs.getDouble(Double.class, "persistedDouble", histMin);
		histMax = prefs.getDouble(Double.class, "persistedDouble", histMax);
		nBins = prefs.getInt(Integer.class, "persistedDouble", nBins);
		String [] ij_args = 
			{ "-Dplugins.dir=C:/Fiji.app/plugins",
			"-Dmacros.dir=C:/Fiji.app/macros" };
		ImageJ.main(ij_args);
		CheckAndSaveResult cs = new CheckAndSaveResult();

		cs.run(null);
	}
	
public Boolean addDialog() {
		
		final Font highlightFont = new Font("Helvetica", Font.BOLD, 12);
		//final Color highlightColor = Color.BLUE;
		GenericDialogPlus gd = new GenericDialogPlus("Get analysis result from RSOM images");
		//	file open option group
		String fileOpenMessage = "RSOM image stack:";
		gd.setInsets(10,0,0);
		gd.addMessage(fileOpenMessage, highlightFont);
		gd.setInsets(0,0,0);
		gd.addDirectoryOrFileField("Select RSOM image stack:", imgPath);
		gd.setInsets(0,163,0);
		gd.addCheckbox("Use input image calibration", useCalibration);
		gd.setInsets(0,0,0);
		gd.addNumericField("X:", voxelSizeX, 1);
		gd.setInsets(0,0,0);
		gd.addNumericField("Y:", voxelSizeY, 1);
		gd.setInsets(0,0,0);
		gd.addNumericField("Z:", voxelSizeZ, 1);
		gd.setInsets(0,0,0);
		gd.addStringField("Unit:", calUnit);
		
		//	Analysis options group
		String saveMessage = "Analysis options:";
		gd.setInsets(10,0,0);
		gd.addMessage(saveMessage, highlightFont);
		gd.setInsets(0,163,0);
		gd.addCheckbox("slice-wise result", getSlc);
		gd.setInsets(0,163,0);
		gd.addCheckbox("ROI-wise result", getRoi);
		gd.setInsets(0,163,0);
		gd.addCheckbox("object-wise result", getObj);
		String[] connChoice = new String[] {"26", "6"};
		gd.addChoice("3D ojbect connectivity:", connChoice, connChoice[connIdx]);
		
		gd.setInsets(0,163,0);
		gd.addCheckbox("diameter histogram", getHst);
		gd.addNumericField("histogram minimum value:", histMin, 2);
		gd.addNumericField("histogram maximum value:", histMax, 2);
		gd.addNumericField("number of bins:", nBins, 0);
		
		gd.showDialog();
		
		imgPath = gd.getNextString();
		useCalibration = gd.getNextBoolean();
		voxelSizeX = gd.getNextNumber();
		voxelSizeY = gd.getNextNumber();
		voxelSizeZ = gd.getNextNumber();
		calUnit = gd.getNextString();
		
		getSlc = gd.getNextBoolean();
		getRoi = gd.getNextBoolean();
		getObj = gd.getNextBoolean();
		connIdx = gd.getNextChoiceIndex();
		connectivity = Integer.parseInt(connChoice[connIdx]);
		getHst = gd.getNextBoolean();
		histMin = gd.getNextNumber();
		histMax = gd.getNextNumber();
		nBins = (int)gd.getNextNumber();
		
		if (gd.wasCanceled())	return false;
		ris = new RsomImageStack(imgPath);
		if (ris.RSOMImg == null) return false;
		return true;
		
	}
}
