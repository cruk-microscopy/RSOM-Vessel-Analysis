package uk.ac.cam.cruk.RSOM;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Point;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.prefs.Preferences;

import javax.swing.border.Border;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JProgressBar;

import org.scijava.command.Command;
import org.scijava.command.CommandModule;
import org.scijava.module.ModuleItem;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.Parameter;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.ImageCanvas;
import ij.gui.Roi;
import ij.gui.WaitForUserDialog;
import ij.io.DirectoryChooser;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
//test
import ij.process.ImageProcessor;
import ij.plugin.filter.BackgroundSubtracter;
import ij.plugin.filter.ParticleAnalyzer;
import ij.plugin.frame.RoiManager;
import ij.ImageStack;
//test
import ij.plugin.FolderOpener;
import ij.plugin.PlugIn;

public class RSOM_Vessel_Analysis implements PlugIn {
	
	
	String imageFolder;
	
	
	// input parameters with initialised default value
	//protected static int activeImgCount = WindowManager.getImageCount();
	//protected static int activeImgCount;
	
	//public int imgID;
	
	
	@Override
	public void run(String arg0) {
		
		// check ImageJ version
		if (IJ.versionLessThan("1.52f"))	{
			IJ.log("update ImageJ version");
			return;
		}
		
		/*
		// check 3rd party pluginhjn
		boolean installMorpholibJ = false;
		if (!PluginUtility.pluginCheck("MorphoLibJ", null)) {
			installMorpholibJ = PluginUtility.installMorpholibJ();
			if (!installMorpholibJ) {
				IJ.log("   \"IJPB plugin\" not found!");
				IJ.log("   Install it manually by adding IJPB-plugins to update sites of Fiji.");
				return;
			}
		}
		// Ask User to restart Fiji after newly installed plugin
		if (installMorpholibJ) {
			IJ.log("   3rd party plugin installed.");
			IJ.log("   Restart Fiji to run the RSOM analysis plugin.");
			return;
		}
		*/
		// get start date and time
		long startTime = GetDateAndTime.getCurrentTimeInMs();
		String date = GetDateAndTime.getCurrentDate();
		String time = GetDateAndTime.getCurrentTime();
		
		// show parameter dialog
		// get input parameter
		ParameterDialog pD = new ParameterDialog();
		if (!pD.addDialog()) return;
		
		// initiate and display the progress bar
				ImageJ j = IJ.getInstance();
			    JFrame f = new JFrame("RSOM analysis progress");
			    f.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
			    Container content = f.getContentPane();
			    JProgressBar progressBar = new JProgressBar(0,100);
			    //println(String.valueOf(j.getHeight()));
			    f.setLocation(j.getX()+j.getWidth()+10,j.getY()+5);
			    Border border = BorderFactory.createTitledBorder("Processing start...");
			    progressBar.setBorder(border);
			    content.add(progressBar, BorderLayout.NORTH);
			    f.setSize(400, 100);
			    f.setVisible(true);
			    progressBar.setStringPainted(true);
			    progressBar.setValue(0);
		
			    
		// update progress bar
				border = BorderFactory.createTitledBorder("Loading image...");
			    progressBar.setBorder(border);
				progressBar.setValue(progressBar.getValue()+7);
		// open the indicated RSOM image stack
		ImagePlus inputImg = null;
		if (pD.getActiveImage) {
			int activeImageID = WindowManager.getNthImageID(pD.activeImgNum);
			inputImg = WindowManager.getImage(activeImageID);
		} else {
			if (pD.isImageSequence) {
				inputImg = FolderOpener.open(pD.filePath,"file=tif");
			} else {
				inputImg = IJ.openImage(pD.filePath);
			}
		}
		if (inputImg == null) {
			f.dispose();
			IJ.log("Input RSOM image wrong!");
			return;
		}
		
		if (!pD.getActiveImage) inputImg.show();
		inputImg.getWindow().setVisible(false); // JWindow trick to hide the image from user
		
		// update progress bar
			border = BorderFactory.createTitledBorder("Calibrating image...");
		    progressBar.setBorder(border);
			progressBar.setValue(progressBar.getValue()+1);
		// construct RSOM object with the obtained ImagePlus
	    // duplicate input image to an RSOM image stack ImagePlus
		RsomImageStack RIS = new RsomImageStack(inputImg);
		
		if (pD.doCalibration) {
			RsomImageStack.calibrateImage(
					RIS,
					pD.voxelSizeX,
					pD.voxelSizeY,
					pD.voxelSizeZ,
					pD.calUnit);
		}
		ImagePlus RsomImg = inputImg.duplicate();
	    //RsomImg.setTitle(inputImg.getShortTitle());
		
		// prepare RoiManager for operation
		RoiManager rm = RoiManager.getInstance2();
		Roi[] oriRois = null; Boolean rmHidden = false; Point rmLocation = null;
		if (RoiManagerUtility.isOpen() && !RoiManagerUtility.isEmpty()) {
			oriRois = RoiManagerUtility.managerToRoiArray();
			rmHidden = RoiManagerUtility.isHidden();
			rmLocation = RoiManagerUtility.getLocation();
			RoiManagerUtility.resetManager();
		}
		RoiManagerUtility.hideManager();
		
		// update progress bar
				border = BorderFactory.createTitledBorder("Subtracting background...");
			    progressBar.setBorder(border);
			    progressBar.setValue(progressBar.getValue()+7);
		// subtract background
		//IJ.selectWindow(RsomImg.getID());
		if (pD.doSubtractBackground) {
			BackgroundSubstraction bs = new BackgroundSubstraction();
			RsomImg = bs.subtractBackground(RsomImg,pD.bgRadius);
		}
		
		// update progress bar
				border = BorderFactory.createTitledBorder("Thresholding image...");
			    progressBar.setBorder(border);
				progressBar.setValue(progressBar.getValue()+15);
		// threshold image
		ImagePlus mask = null;	
		ThresholdImage t = new ThresholdImage();
		if (pD.doManualThreshold)	t.run(RsomImg, "Manual", !pD.getActiveImage);
		else	t.run(RsomImg, pD.autoMethod, !pD.getActiveImage);
		mask = t.mask;
		double[] thresholdBounds = t.bounds;
		mask.setCalibration(RsomImg.getCalibration());
		
		// update progress bar
				border = BorderFactory.createTitledBorder("Median filtering...");
			    progressBar.setBorder(border);
				progressBar.setValue(progressBar.getValue()+7);
		// median filter
		if (pD.mdFtRadius != 0) {
			MedianFilter m = new MedianFilter();
			mask = m.medianFilter(mask,pD.mdFtRadius);
		}
		
		// update progress bar
				border = BorderFactory.createTitledBorder("Managing 3D ROI selection...");
			    progressBar.setBorder(border);
				progressBar.setValue(progressBar.getValue()+7);
		ImagePlus objMap = null;
		ImagePlus diaMap = null;
		// get roi 3D
		GetSelection3D s = new GetSelection3D();
		String rp = pD.roiPath;
		//IJ.log("debug: rp, pD.roiPath: "+rp);
		if (pD.doRoi) {
			if (!pD.loadRoi) rp = null;
			rm = s.run(mask, RsomImg, rp);
		} else {
			rm = s.selectAll(mask);
		}
		
		if (false) { //!!!THIS
			ImagePlus selectionImage = SmoothSelection3D.managerToMask();
			SmoothSelection3D.smoothSelection3D(selectionImage, pD.smoothLv, pD.downSizeLv, pD.shrinkSize);

		}
		
		
		if (!RIS.resultDirExist) {
			CheckAndSaveResult.makeResultPaths(RIS);
		}
		mask.hide();
		pD.roiPath = RIS.selectionPath;	rp = pD.roiPath;
		rm.runCommand("Save", rp);
		// update progress bar
			border = BorderFactory.createTitledBorder("Saving Tiff stack...");
		    progressBar.setBorder(border);
			progressBar.setValue(progressBar.getValue()+10);
		if (pD.isImageSequence || pD.doCalibration) {
			RIS.filePath += ".tif";
			CheckAndSaveResult.saveAsTiffStack(RsomImg, RIS.filePath);
		}
		
		// update progress bar
			border = BorderFactory.createTitledBorder("Parsing result saving path ...");
		    progressBar.setBorder(border);
			progressBar.setValue(progressBar.getValue()+1);

		if (!RoiManagerUtility.isEmpty()) {
			CheckAndSaveResult.saveROI(rm, RIS.selectionPath);
			RoiManagerUtility.resetManager();
		}
		
		if (pD.saveMask) {
			// update progress bar
						border = BorderFactory.createTitledBorder("Saving mask image...");
					    progressBar.setBorder(border);
						progressBar.setValue(progressBar.getValue()+5);
			//IJ.log("debug- RIS.maskPath: "+ RIS.maskPath);
			CheckAndSaveResult.saveMask(mask, RIS.maskPath);
		}
		
		if (pD.saveROI) {
			// update progress bar
						border = BorderFactory.createTitledBorder("Saving 2D ROIset ...");
					    progressBar.setBorder(border);
						progressBar.setValue(progressBar.getValue()+5);
			rm = ConnectComponentAnalysis.CC_2D(mask);
			CheckAndSaveResult.saveROI(rm, RIS.roiPath);
			//rm.reset(); rm.close();
			RIS.RSOMImg.deleteRoi();
		}
		
		if (pD.saveObjMap) {
			// update progress bar
						border = BorderFactory.createTitledBorder("Saving objects map ...");
					    progressBar.setBorder(border);
						progressBar.setValue(progressBar.getValue()+10);
			mask.deleteRoi();
			ConnectComponentAnalysis cc = new ConnectComponentAnalysis(
					mask,
					pD.connectivity,
					32
					);
			objMap = cc.CC_3D(mask, pD.connectivity, 32, RIS.imageName);
			objMap.setCalibration(RsomImg.getCalibration());
			CheckAndSaveResult.saveObjMap(objMap, RIS.objectMapPath);
			//rm.reset(); rm.close();
			RIS.RSOMImg.deleteRoi();
		}
		
		if (pD.saveDiameterMap) {
			// update progress bar
					border = BorderFactory.createTitledBorder("Saving diameter map ...");
				    progressBar.setBorder(border);
					progressBar.setValue(progressBar.getValue()+10);
			mask.deleteRoi();
			LocalThicknessAnalysis l = new LocalThicknessAnalysis(mask, RIS.RSOMImg.getCalibration(), "default", RIS.imageName);
			//diaMap = LocalThicknessAnalysis.getThicknessMap(mask);
			diaMap = l.run();
			diaMap.setCalibration(RsomImg.getCalibration());
			CheckAndSaveResult.saveDiameterMap(diaMap, RIS.diameterMapPath);
		}
		
		// update progress bar
			border = BorderFactory.createTitledBorder("Saving slice-wise result ...");
		    progressBar.setBorder(border);
			progressBar.setValue(progressBar.getValue()+5);
		RoiManagerUtility.resetManager();
		rm = RoiManager.getInstance2();
		rm.runCommand("Open", pD.roiPath); // RoiManager load 3D selection
		if (!CheckAndSaveResult.saveSliceWiseResult (
				inputImg,
				mask,
				RIS.calibration,
				RIS.calUnit,
				rm,
				RIS.resultSlicePath,
				true,
				false)) {
			IJ.log("Slice-wise result not saved.");
		}
		// generate and save ROI-wise result
		// update progress bar
				border = BorderFactory.createTitledBorder("Saving ROI-wise result ...");
			    progressBar.setBorder(border);
				progressBar.setValue(progressBar.getValue()+5);
		RoiManagerUtility.resetManager();
		rm = RoiManager.getInstance2();
		rm.runCommand("Open", RIS.roiPath); // RoiManager load 2D ROiset
		if (!CheckAndSaveResult.saveRoiWiseResult (
			inputImg,
			mask,
			objMap,
			diaMap,
			RIS.calibration,
			RIS.calUnit,
			rm,
			RIS.resultRoiPath,
			true,
			false)) {
		IJ.log("ROI-wise result not saved.");
		}
		
		
		// generate and save Object-wise result
		// update progress bar
			border = BorderFactory.createTitledBorder("Saving object-wise result ...");
		    progressBar.setBorder(border);
			progressBar.setValue(progressBar.getValue()+5);
		if (!CheckAndSaveResult.saveObjWiseResult (
				inputImg,
				mask,
				objMap,
				pD.connectivity,
				diaMap,
				RIS.calibration,
				RIS.calUnit,
				rm,
				RIS.resultObjectPath,
				true,
				false)) {
		IJ.log("Object-wise result not saved.");
		}
		// RoiManager is no longer needed for the operation.
		RoiManagerUtility.closeManager();
		
		
		// update progress bar
				border = BorderFactory.createTitledBorder("Saving histogram ...");
			    progressBar.setBorder(border);
				progressBar.setValue(progressBar.getValue()+5);
		if (!CheckAndSaveResult.saveHisto (
				diaMap,
				0,
				0,
				pD.nBins,
				RIS.resultHistogramPath,
				true,
				false)) {
		IJ.log("diameter histogram not saved.");
		}
		
		
		// update progress bar
			border = BorderFactory.createTitledBorder("Cleaning up ...");
		    progressBar.setBorder(border);
			progressBar.setValue(progressBar.getValue()+5);
		// clean up, report, revert changes, and return
		RsomImg.flush(); RsomImg.close();
		mask.changes = false;	mask.flush(); mask.close(); 
		objMap.flush(); objMap.close(); 
		diaMap.flush(); diaMap.close(); 
		// if input image is taken from active image, revert changes and display it
		// if load from disk, close it
		inputImg.deleteRoi();	inputImg.setOverlay(null);
		if (pD.getActiveImage) {
			inputImg.revert();
			ImageCanvas ic = inputImg.getCanvas();
			if (ic!=null) ic.setShowAllList(null);
			inputImg.updateAndRepaintWindow();
			inputImg.getWindow().setVisible(true);
		} else {
			inputImg.close();
		}
		
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
					pD, 
					RIS, 
					thresholdBounds,
					pD.connectivity,
					date, 
					time, 
					duration, 
					RIS.executionLogPath)) {
				IJ.log("Current operation has not been logged.");
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		
		// complete and close progress bar
			border = BorderFactory.createTitledBorder("Operation complete ...");
		    progressBar.setBorder(border);
			progressBar.setValue(100);
		f.dispose();
		IJ.run("Collect Garbage", "");
		IJ.log("Operation complete.");
		
		System.gc(); // system garbage collector (release memory back to computer)
		return;	
	}
	
	public static void batchRun (
			File[] tifFileList,
			int mode	//0: full; 1: only image
			) {
		
		ParameterDialog pD = new ParameterDialog();
		if (!pD.addDialog()) return;
		String date = GetDateAndTime.getCurrentDate();
		String time = GetDateAndTime.getCurrentTime();
		
		for (File f : tifFileList) {
			
			long startTime = GetDateAndTime.getCurrentTimeInMs();

			ImagePlus inputImg = null;
			try {
				inputImg = IJ.openImage(f.getCanonicalPath());
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			if (inputImg == null)
				continue;
			
			// construct RSOM object with the obtained ImagePlus
		    // duplicate input image to an RSOM image stack ImagePlus
			RsomImageStack RIS = new RsomImageStack(inputImg);
			
			if (pD.doCalibration) {
				RsomImageStack.calibrateImage(
						RIS,
						pD.voxelSizeX,
						pD.voxelSizeY,
						pD.voxelSizeZ,
						pD.calUnit);
			}
			
			ImagePlus RsomImg = inputImg.duplicate();
		    //RsomImg.setTitle(inputImg.getShortTitle());
			
			// prepare RoiManager for operation
			RoiManager rm = RoiManager.getInstance2();
			Roi[] oriRois = null; Boolean rmHidden = false; Point rmLocation = null;
			if (RoiManagerUtility.isOpen() && !RoiManagerUtility.isEmpty()) {
				oriRois = RoiManagerUtility.managerToRoiArray();
				rmHidden = RoiManagerUtility.isHidden();
				rmLocation = RoiManagerUtility.getLocation();
				RoiManagerUtility.resetManager();
			}
			RoiManagerUtility.hideManager();
			
			// subtract background
			//IJ.selectWindow(RsomImg.getID());
			if (pD.doSubtractBackground) {
				BackgroundSubstraction bs = new BackgroundSubstraction();
				RsomImg = bs.subtractBackground(RsomImg,pD.bgRadius);
			}
			
			// threshold image
			ImagePlus mask = null;	
			ThresholdImage t = new ThresholdImage();
			
			t.run(RsomImg, pD.autoMethod, true);
			mask = t.mask;
			double[] thresholdBounds = t.bounds;
			mask.setCalibration(RsomImg.getCalibration());
			
			// median filter
			if (pD.mdFtRadius != 0) {
				MedianFilter m = new MedianFilter();
				mask = m.medianFilter(mask,pD.mdFtRadius);
			}
			
			ImagePlus objMap = null;
			ImagePlus diaMap = null;
			// get roi 3D
			GetSelection3D s = new GetSelection3D();
			String rp = RIS.selectionPath;
			if (pD.doRoi) {
				if (!pD.loadRoi) rp = null;
				rm = s.run(mask, RsomImg, rp);
			} else {
				rm = s.selectAll(mask);
			}
			
			if (!RIS.resultDirExist) {
				CheckAndSaveResult.makeResultPaths(RIS);
			}
			mask.hide();
			pD.roiPath = RIS.selectionPath;	rp = pD.roiPath;
			rm.runCommand("Save", rp);
			if (!RoiManagerUtility.isEmpty()) {
				CheckAndSaveResult.saveROI(rm, RIS.selectionPath);
				RoiManagerUtility.resetManager();
			}
			if (pD.saveMask) {
				CheckAndSaveResult.saveMask(mask, RIS.maskPath);
			}
			if (pD.saveROI) {
				rm = ConnectComponentAnalysis.CC_2D(mask);
				CheckAndSaveResult.saveROI(rm, RIS.roiPath);
				RIS.RSOMImg.deleteRoi();
			}
			if (pD.saveObjMap) {
				mask.deleteRoi();
				ConnectComponentAnalysis cc = new ConnectComponentAnalysis(mask,pD.connectivity,32);
				objMap = cc.CC_3D(mask, pD.connectivity, 32, RIS.imageName);
				objMap.setCalibration(RsomImg.getCalibration());
				CheckAndSaveResult.saveObjMap(objMap, RIS.objectMapPath);
				RIS.RSOMImg.deleteRoi();
			}
			if (pD.saveDiameterMap) {
				mask.deleteRoi();
				LocalThicknessAnalysis l = new LocalThicknessAnalysis(mask, RIS.RSOMImg.getCalibration(), "default", RIS.imageName);
				//diaMap = LocalThicknessAnalysis.getThicknessMap(mask);
				diaMap = l.run();
				diaMap.setCalibration(RsomImg.getCalibration());
				CheckAndSaveResult.saveDiameterMap(diaMap, RIS.diameterMapPath);
			}
			RoiManagerUtility.resetManager();
			
			if (mode == 0) {
				rm = RoiManager.getInstance2();
				rm.runCommand("Open", pD.roiPath); // RoiManager load 3D selection
				if (!CheckAndSaveResult.saveSliceWiseResult (inputImg,mask,RIS.calibration,RIS.calUnit,rm,RIS.resultSlicePath,true,false)) {
					IJ.log("Slice-wise result not saved.");
				}
				RoiManagerUtility.resetManager();
				rm = RoiManager.getInstance2();
				rm.runCommand("Open", RIS.roiPath); // RoiManager load 2D ROiset
				if (!CheckAndSaveResult.saveRoiWiseResult (inputImg,mask,objMap,diaMap,RIS.calibration,RIS.calUnit,rm,RIS.resultRoiPath,true,false)) {
					IJ.log("ROI-wise result not saved.");
				}
				if (!CheckAndSaveResult.saveObjWiseResult (inputImg,mask,objMap,pD.connectivity,diaMap,RIS.calibration,RIS.calUnit,rm,RIS.resultObjectPath,true,false)) {
					IJ.log("Object-wise result not saved.");
				}
				// RoiManager is no longer needed for the operation.
				RoiManagerUtility.resetManager();
				if (!CheckAndSaveResult.saveHisto (diaMap,0,0,pD.nBins,RIS.resultHistogramPath,true,false)) {
					IJ.log("diameter histogram not saved.");
				}
			}
			// clean up, report, revert changes, and return
			RsomImg.flush(); RsomImg.close();
			mask.changes = false;	mask.flush(); mask.close(); 
			objMap.flush(); objMap.close(); 
			diaMap.flush(); diaMap.close(); 
			// if input image is taken from active image, revert changes and display it
			// if load from disk, close it
			inputImg.close();

			// save runtime log
			long endTime = GetDateAndTime.getCurrentTimeInMs();
			String duration = GetDateAndTime.getDuration(endTime - startTime);
			
			// create and save log; if log file exist, append in the end
			try {
				if (!CheckAndSaveResult.saveLog(pD,RIS,thresholdBounds,pD.connectivity,date,time,duration,RIS.executionLogPath)) {
					IJ.log("Current operation has not been logged.");
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
	
			IJ.run("Collect Garbage", "");
			IJ.log("Operation complete.");
			System.gc(); // system garbage collector (release memory back to computer)
		}

		return;	
	}
	
	public static void main(String[] args) {
		
		if (IJ.versionLessThan("1.52f")) System.exit(0);

		String [] ij_args = 
			{ "-Dplugins.dir=C:/Fiji.app/plugins",
			"-Dmacros.dir=C:/Fiji.app/macros" };

		ij.ImageJ.main(ij_args);
		
		RSOM_Vessel_Analysis np = new RSOM_Vessel_Analysis();
		np.run(null);
		
	}

	
}
