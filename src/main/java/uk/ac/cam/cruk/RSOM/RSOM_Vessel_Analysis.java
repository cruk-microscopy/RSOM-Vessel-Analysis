package uk.ac.cam.cruk.RSOM;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Point;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
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
import ij.io.FileSaver;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
//test
import ij.process.ImageProcessor;
import uk.ac.cam.cruk.RSOM.ParameterDialog.methodSet;
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
		
		// get start date and time
		long startTime = GetDateAndTime.getCurrentTimeInMs();
		String date = GetDateAndTime.getCurrentDate();
		String time = GetDateAndTime.getCurrentTime();
		
		// show parameter dialog
		// get input parameter
		
		ParameterDialog pD = new ParameterDialog();
		if (!pD.addDialog()) return;
		
		IJ.log("\n\nRSOM vessel analysis executed at:");
		IJ.log(date + " " + time);
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
		RsomImageStack RIS = null;
		if (pD.getActiveImage) {
			int activeImageID = WindowManager.getNthImageID(pD.activeImgNum);
			inputImg = WindowManager.getImage(activeImageID);
			RIS = new RsomImageStack(inputImg);
		} else {
			RIS = new RsomImageStack(pD.filePath);
		}
		
		//inputImg = RIS.RSOMImg;
		
		if (RIS.RSOMImg == null) {
			f.dispose();
			IJ.log("Input RSOM image wrong!");
			return;
		}
		
		//if (!pD.getActiveImage) 
		RIS.RSOMImg.show();
		//inputImg.getWindow().setVisible(false); // JWindow trick to hide the image from user
		RIS.RSOMImg.getWindow().setVisible(false);
		
		// update progress bar
			border = BorderFactory.createTitledBorder("Calibrating image...");
		    progressBar.setBorder(border);
			progressBar.setValue(progressBar.getValue()+1);
		// construct RSOM object with the obtained ImagePlus
	    // duplicate input image to an RSOM image stack ImagePlus
		
		// 2020.02.25, required by Emma
		CheckAndSaveResult.makeResultPaths(RIS);
		//if (!RIS.resultDirExist) {
		//	CheckAndSaveResult.makeResultPaths(RIS);
		//}
		
				/* debugging of the input image
				IJ.log("       RSOM main debug begin: ");
				IJ.log("       RIS is sequence: " + String.valueOf(RIS.isSequence));
				IJ.log("       RIS resultDirExist: " + String.valueOf(RIS.resultDirExist));
				IJ.log("       RIS filePath : " + String.valueOf(RIS.filePath));
				IJ.log("       RIS dirPath : " + String.valueOf(RIS.dirPath));
				IJ.log("       RIS imageTitle : " + String.valueOf(RIS.imageTitle));
				IJ.log("       RIS imageName : " + String.valueOf(RIS.imageName));
				IJ.log("       RIS sampleCode : " + String.valueOf(RIS.sampleCode));
				IJ.log("       RIS resultDir : " + String.valueOf(RIS.resultDir));
				IJ.log("       RIS manualROIPath : " + String.valueOf(RIS.manualROIPath));
				IJ.log("       RIS roiPath : " + String.valueOf(RIS.roiPath));
				IJ.log("       RIS selectionPath : " + String.valueOf(RIS.selectionPath));
				IJ.log("       RSOM main debug finish: ");
				*/
		
		/*
		if (RIS.isSequence) {	// RIS is loaded from image sequence
			// check if the corresponding TIF exist
			FileSaver fs = new FileSaver(inputImg);
			fs.saveAsTiffStack(RIS.dirPath + File.separator + RIS.sampleCode + ".tif");
		}*/
		
		
		
		if (pD.doCalibration) {
			RsomImageStack.calibrateImage(
					RIS,
					pD.voxelSizeX,
					pD.voxelSizeY,
					pD.voxelSizeZ,
					pD.calUnit);
		}
		
		
		ImagePlus RsomImg = RIS.RSOMImg.duplicate();
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
		if (pD.doSubtractBackground && pD.bgRadius>0) {
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
				progressBar.setValue(progressBar.getValue()+5);
		
		
		// get roi 3D
		GetSelection3D s = new GetSelection3D();
		String rp = pD.selectionPath;
		//IJ.log("debug: rp, pD.roiPath: "+rp);
		if (pD.doRoi) {	// operate only within selected area of image
			if (!pD.loadRoi) rp = null;	// if no roi path was given, ask user to draw the ROI on the run
			s.run(mask, RsomImg, rp, RIS.manualROIPath);
		} else {	// operate using the whole image as input
			s.selectAll(mask);
			rm = RoiManager.getInstance2();
			rm.runCommand("Save", RIS.manualROIPath);
		}
		
		RoiManagerUtility.hideManager();
		rm = RoiManager.getInstance2();
		mask.hide();
		//pD.selectionPath = RIS.manualROIPath;	rp = pD.selectionPath;
		// smooth 3D selection
		//if (pD.doSmooth) {
		//IJ.log(String.valueOf(pD.shrinkSize/pD.voxelSizeX));
		if (pD.doRoi && pD.doSmooth) {
			// update progress bar
						border = BorderFactory.createTitledBorder("Smoothing 3D ROI selection...");
					    progressBar.setBorder(border);
						progressBar.setValue(progressBar.getValue()+2);
						
			//ImagePlus unsmoothedMask = SmoothSelection3D.managerToMask();
			//if (unsmoothedMask != null) {
				//ImagePlus smoothedMask = SmoothSelection3D.smoothSelection3D(unsmoothedMask, pD.smoothLv, pD.downSizeLv, 0);
				//smoothedMask.setTitle("smoothed from plugin");
				//smoothedMask.show();
				//smoothedMask.show();
				//SmoothSelection3D.maskToRoiManager(smoothedMask, pD.shrinkSize/pD.voxelSizeX);
				//smoothedMask.changes = false;
				//smoothedMask.close();
				//IJ.run("Collect Garbage", "");
						
				SmoothSelection3D.smoothSelectionWithRoiManager(pD.smoothLv, pD.downSizeLv, pD.shrinkSize/pD.voxelSizeX);
				//System.gc();
			//}
		}
		if (rm!= null || rm.getCount()!=0) {
			rm.runCommand("Save", RIS.selectionPath);
		} else {
			IJ.log("Parameter wrong! 3D selection can not be generated from manual ROI.");
			IJ.log("Most probably the smooth/shrink setting is not proper.");
			IJ.log("From here on, processing will be performed on whole image.");
		}
		//else Report the smooth and shrink has error so from now on, whole image will be processed
		// clear mask image to content only within the (3D) selected area
		GetSelection3D.clearImageWithSelection(mask);
		
		// update progress bar
		//	border = BorderFactory.createTitledBorder("Saving Tiff stack...");
		//    progressBar.setBorder(border);
		//	progressBar.setValue(progressBar.getValue()+15);
			
		/*	
		if (pD.isImageSequence || pD.doCalibration) {
			RIS.filePath += ".tif";
			CheckAndSaveResult.saveAsTiffStack(RsomImg, RIS.filePath);
		}
		*/
		
		// update progress bar
			border = BorderFactory.createTitledBorder("Parsing result saving path ...");
		    progressBar.setBorder(border);
			progressBar.setValue(progressBar.getValue()+1);

		
		
		if (pD.saveMask) {
			// update progress bar
						border = BorderFactory.createTitledBorder("Saving mask image...");
					    progressBar.setBorder(border);
						progressBar.setValue(progressBar.getValue()+5);
			//IJ.log("debug- RIS.maskPath: "+ RIS.maskPath);
			CheckAndSaveResult.saveMask(mask, RIS.maskPath);
		}
		
		//if (!RoiManagerUtility.isEmpty()) {
			//CheckAndSaveResult.saveROI(rm, RIS.selectionPath);
		//	RoiManagerUtility.resetManager();
		//}
		RoiManagerUtility.resetManager();
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
		
		ImagePlus objMap = null;
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
		ImagePlus diaMap = null;
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
		
		if (pD.saveMask) {
		// update progress bar
				border = BorderFactory.createTitledBorder("Saving slice-wise result ...");
			    progressBar.setBorder(border);
				progressBar.setValue(progressBar.getValue()+5);
			RoiManagerUtility.resetManager();
			rm = RoiManager.getInstance2();
			rm.runCommand("Open", RIS.selectionPath); // RoiManager load 3D selection
			if (!CheckAndSaveResult.saveSliceWiseResult (
					RIS.RSOMImg,
					mask,
					RIS.calibration,
					RIS.calUnit,
					rm,
					RIS.resultSlicePath,
					true,
					false)) {
				IJ.log("slice-wise result not saved.");
			}
		}
		
		if (pD.saveMask && pD.saveObjMap && pD.saveDiameterMap) {
		// generate and save ROI-wise result
		// update progress bar
				border = BorderFactory.createTitledBorder("Saving object2D-wise result ...");
			    progressBar.setBorder(border);
				progressBar.setValue(progressBar.getValue()+5);
			RoiManagerUtility.resetManager();
			rm = RoiManager.getInstance2();
			rm.runCommand("Open", RIS.roiPath); // RoiManager load 2D ROiset
			if (!CheckAndSaveResult.saveRoiWiseResult (
				RIS.RSOMImg,
				mask,
				objMap,
				diaMap,
				RIS.calibration,
				RIS.calUnit,
				rm,
				RIS.resultRoiPath,
				true,
				false)) {
			IJ.log("object2D-wise result not saved.");
			}
		}
		
		if (pD.saveMask && pD.saveObjMap && pD.saveDiameterMap) {
		// generate and save Object-wise result
		// update progress bar
				border = BorderFactory.createTitledBorder("Saving object3D-wise result ...");
			    progressBar.setBorder(border);
				progressBar.setValue(progressBar.getValue()+5);
			if (!CheckAndSaveResult.saveObjWiseResult (
					RIS.RSOMImg,
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
			IJ.log("object3D-wise result not saved.");
			}
		}
		// RoiManager is no longer needed for the operation.
		RoiManagerUtility.closeManager();
		
		if (pD.saveDiameterMap) {
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
		}
		
		// update progress bar
			border = BorderFactory.createTitledBorder("Cleaning up ...");
		    progressBar.setBorder(border);
			progressBar.setValue(progressBar.getValue()+5);
		// clean up, report, revert changes, and return
		RsomImg.changes = false;
		RsomImg.flush(); RsomImg.close();
			
		if (mask!=null) {
			mask.changes = false; mask.flush(); mask.close(); 
		}
		if (objMap!=null) {
			objMap.flush(); objMap.close();
		}
		if (diaMap!=null) {
			diaMap.flush(); diaMap.close();
		}
		// if input image is taken from active image, revert changes and display it
		// if load from disk, close it
		//inputImg.deleteRoi();	inputImg.setOverlay(null);
		RIS.RSOMImg.deleteRoi(); RIS.RSOMImg.setOverlay(null);
		if (pD.getActiveImage) {
			RIS.RSOMImg.changes=false;
			RIS.RSOMImg.revert();
			ImageCanvas ic = inputImg.getCanvas();
			if (ic!=null) ic.setShowAllList(null);
			RIS.RSOMImg.updateAndRepaintWindow();
			RIS.RSOMImg.getWindow().setVisible(true);
		} else {
			RIS.RSOMImg.close();
		}
		
		// return original RoiManager, modify exist=true, append = false (overwrite)
		// revert original display mode: hide or show
		RoiManagerUtility.roiArrayToManager(oriRois, false);
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
		long durationInSecond = (endTime-startTime)/1000;
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
		
		
		IJ.log("Operation complete after " + String.valueOf(durationInSecond) + " seconds.");
		
		System.gc(); // system garbage collector (release memory back to computer)
		return;	
	}
	
	public static void batchRun (
			File[] tifFileList,
			int mode//1: Complete process; 
					//2: Generate mask;
					//3: Get Analysis Result;
			) throws IOException, NullPointerException {
		
		// check ImageJ version
		if (IJ.versionLessThan("1.52f"))	{
			IJ.log("update ImageJ version");
			return;
		}

		ParameterDialog pD = new ParameterDialog();
		if (!pD.addDialog()) return;

		String date = GetDateAndTime.getCurrentDate();
		String time = GetDateAndTime.getCurrentTime();
		IJ.log("\n\nRSOM vessel analysis batch processing executed at:");
		IJ.log(date + " " + time);
		switch (mode) {
		case 1:
			IJ.log(" batch mode: complete process");
			break;
		case 2:
			IJ.log(" batch mode: only generate mask");
			break;
		case 3:
			IJ.log(" batch mode: get analysis result from generated mask");
			break;
		}
		// prepare RoiManager for batch processing
		// prepare RoiManager for operation
		RoiManager rm = RoiManager.getInstance2();
		if (rm == null) rm = new RoiManager();
		else rm.reset();
		rm.setVisible(false);
					
		// process through the file list			
		for (File f : tifFileList) {
			IJ.log("  Processing File: " + f.getCanonicalPath());
			long startTime = GetDateAndTime.getCurrentTimeInMs();

			ImagePlus inputImg = null;
			try {
				RsomImageStack RIS = new RsomImageStack(f.getCanonicalPath());
				inputImg = RIS.RSOMImg;
				if (!RIS.resultDirExist) {
					CheckAndSaveResult.makeResultPaths(RIS);
					IJ.log("  File: "+ f.getCanonicalPath() + " doesn't have result folder yet.");
					IJ.log("   An empty result folder has been created with the current operation.");
					//continue;
				}
				ImagePlus RsomImg = inputImg.duplicate();
				ImagePlus mask = null;
				double[] thresholdBounds = null;
				// for complete process and only-generate-mask 
				// do calibration, background subtraction, thresholding, and median filtering
				if (mode==1 || mode ==2) {
					if (pD.doCalibration) {
						RsomImageStack.calibrateImage(RIS, pD.voxelSizeX, pD.voxelSizeY, pD.voxelSizeZ, pD.calUnit);
					}
					rm.reset();
					// subtract background
					if (pD.doSubtractBackground) {
						BackgroundSubstraction bs = new BackgroundSubstraction();
						RsomImg = bs.subtractBackground(RsomImg,pD.bgRadius);
					}
					// threshold image
					ThresholdImage t = new ThresholdImage();
					t.run(RsomImg, pD.autoMethod, true);
					mask = t.mask;
					thresholdBounds = t.bounds;
					mask.setCalibration(RsomImg.getCalibration());
					// median filter
					if (pD.mdFtRadius != 0) {
						MedianFilter m = new MedianFilter();
						mask = m.medianFilter(mask,pD.mdFtRadius);
					}
					// get roi 3D
					GetSelection3D s = new GetSelection3D();
					boolean manualSelectionExist = false;
					boolean selection3DExist = false;
					if (new File(RIS.selectionPath).exists()) selection3DExist = true;
					if (new File(RIS.manualROIPath).exists()) manualSelectionExist = true;
					
					if (!pD.doRoi) {	
						s.selectAll(mask);
					} else if (!manualSelectionExist) {
						if (!selection3DExist) {
							IJ.log("Can not locate manual ROI or selection3D file, process whole image instead.");
							s.selectAll(mask);
						} else {
							rm.runCommand("Open",RIS.selectionPath);
						}
					} else {	// use manual ROI to generate selection anew
						rm.runCommand("Open",RIS.manualROIPath);
						rm.runCommand(mask,"Sort");
						rm.runCommand(mask,"Interpolate ROIs");
						if (pD.doSmooth) {
							SmoothSelection3D.smoothSelectionWithRoiManager(pD.smoothLv, pD.downSizeLv, pD.shrinkSize/pD.voxelSizeX);
						}
					}
					rm.runCommand("Save", RIS.selectionPath);
					// clear mask image to content only within the (3D) selected area
					GetSelection3D.clearImageWithSelection(mask);
					rm.reset();
					if (pD.saveMask) {
						CheckAndSaveResult.saveMask(mask, RIS.maskPath);
					}
				} else {
					mask = IJ.openImage(RIS.maskPath);
					String lowerBound = CheckAndSaveResult.searchLogForEntry(RIS.executionLogPath, "thresholding bounds:", 1);
					String upperBound = CheckAndSaveResult.searchLogForEntry(RIS.executionLogPath, "thresholding bounds:", 2);
					thresholdBounds = new double[] {Double.valueOf(lowerBound), Double.valueOf(upperBound)};
				}
				
				if (mode==1 || mode==3) {
					if (pD.saveROI) {
						rm = ConnectComponentAnalysis.CC_2D(mask);
						CheckAndSaveResult.saveROI(rm, RIS.roiPath);
						//RIS.RSOMImg.deleteRoi();
					}
					rm.reset();
					ImagePlus objMap = null; 
					if (pD.saveObjMap) {
						mask.deleteRoi();
						ConnectComponentAnalysis cc = new ConnectComponentAnalysis(mask,pD.connectivity,32);
						objMap = cc.CC_3D(mask, pD.connectivity, 32, RIS.imageName);
						objMap.setCalibration(RsomImg.getCalibration());
						CheckAndSaveResult.saveObjMap(objMap, RIS.objectMapPath);
						RIS.RSOMImg.deleteRoi();
					}
					ImagePlus diaMap = null;
					if (pD.saveDiameterMap) {
						mask.deleteRoi();
						LocalThicknessAnalysis l = new LocalThicknessAnalysis(mask, RIS.RSOMImg.getCalibration(), "default", RIS.imageName);
						diaMap = l.run();
						diaMap.setCalibration(RsomImg.getCalibration());
						CheckAndSaveResult.saveDiameterMap(diaMap, RIS.diameterMapPath);
					}
					if (pD.saveMask) {
						rm.reset();
						rm.runCommand("Open", RIS.selectionPath); // RoiManager load 3D selection
						if (!CheckAndSaveResult.saveSliceWiseResult (inputImg, mask, RIS.calibration, RIS.calUnit, rm, RIS.resultSlicePath,	true, false)) {
							IJ.log("   slice-wise result not saved.");
						}
					}
					if (pD.saveMask && pD.saveObjMap && pD.saveDiameterMap) {
						rm.reset();
						rm.runCommand("Open", RIS.roiPath); // RoiManager load 2D ROiset
						if (!CheckAndSaveResult.saveRoiWiseResult (inputImg, mask, objMap, diaMap, RIS.calibration, RIS.calUnit, rm, RIS.resultRoiPath, true, false)) {
							IJ.log("   object2D-wise result not saved.");
						}
					}
					if (pD.saveMask && pD.saveObjMap && pD.saveDiameterMap) {
						if (!CheckAndSaveResult.saveObjWiseResult (inputImg, mask, objMap, pD.connectivity, diaMap, RIS.calibration, RIS.calUnit, rm, RIS.resultObjectPath, true, false)) {
							IJ.log("   object3D-wise result not saved.");
						}
					}
					// RoiManager is no longer needed for the current operation.
					rm.reset();
					if (pD.saveDiameterMap) {
						if (!CheckAndSaveResult.saveHisto (diaMap, 0, 0, pD.nBins, RIS.resultHistogramPath, true, false)) {		
							IJ.log("   diameter histogram not saved.");
						}
					}
					// clean up, report, revert changes, and return
					RsomImg.changes = false; RsomImg.close();
					mask.changes = false;	mask.close(); 
					objMap.changes = false; objMap.close(); 
					diaMap.changes = false; diaMap.close(); 
					inputImg.changes = false;	inputImg.close();
				}
				// save runtime log
				long endTime = GetDateAndTime.getCurrentTimeInMs();
				String duration = GetDateAndTime.getDuration(endTime - startTime);
				try {
					if (!CheckAndSaveResult.saveLog(pD,RIS, thresholdBounds,pD.connectivity,date,time,duration,RIS.executionLogPath)) {
						IJ.log("   Current operation has not been logged.");
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
				IJ.run("Collect Garbage", "");
				IJ.log("  File: " + f.getName() + " processed, took " + String.valueOf((endTime-startTime)/1000) + " seconds.");
			} catch (NullPointerException e) {
				IJ.log("   !!Missing neccessary file(s) for the current operation!!");
				IJ.log("   File: " + f.getName() + " not processed properly.");
				e.printStackTrace();
			}
		}
		rm.setVisible(true);
		rm.close();
		System.gc(); // system garbage collector (release memory back to computer)	
	}
	
	public static void batchRun2 (
			File[] tifFileList
			) throws IOException, NullPointerException {
		ParameterDialog pD = new ParameterDialog();
		if (!pD.addDialog()) return;
		
		String resultDir = IJ.getDirectory("select result folder");
		if (null==resultDir) return;
		if (!resultDir.endsWith(File.separator)) resultDir += File.separator;

		String date = GetDateAndTime.getCurrentDate();
		String time = GetDateAndTime.getCurrentTime();
		IJ.log("\n\nRSOM vessel analysis batch processing executed at:");
		IJ.log(date + " " + time);
		
		// prepare RoiManager for batch processing
		// prepare RoiManager for operation
		RoiManager rm = RoiManager.getInstance2();
		if (rm == null) rm = new RoiManager();
		else rm.reset();
		rm.setVisible(false);
					
		// process through the file list			
		for (File f : tifFileList) {
			IJ.log("  Processing File: " + f.getCanonicalPath());
			long startTime = GetDateAndTime.getCurrentTimeInMs();

			ImagePlus inputImg = null;
			try {
				RsomImageStack RIS = new RsomImageStack(f.getCanonicalPath());
				inputImg = RIS.RSOMImg;
				
				ImagePlus RsomImg = inputImg.duplicate();
				ImagePlus mask = null;
				double[] thresholdBounds = null;
				// for complete process and only-generate-mask 
				// do calibration, background subtraction, thresholding, and median filtering
				if (pD.doCalibration)
					RsomImageStack.calibrateImage(RIS, pD.voxelSizeX, pD.voxelSizeY, pD.voxelSizeZ, pD.calUnit);
					
				rm.reset();
				// subtract background
				if (pD.doSubtractBackground) {
					BackgroundSubstraction bs = new BackgroundSubstraction();
					RsomImg = bs.subtractBackground(RsomImg,pD.bgRadius);
				}
				// threshold image
				ThresholdImage t = new ThresholdImage();
				t.run(RsomImg, pD.autoMethod, true);
				mask = t.mask;
				thresholdBounds = t.bounds;
				mask.setCalibration(RsomImg.getCalibration());
				// median filter
				if (pD.mdFtRadius != 0) {
					MedianFilter m = new MedianFilter();
					mask = m.medianFilter(mask,pD.mdFtRadius);
				}
				// get roi 3D
				/*
				GetSelection3D s = new GetSelection3D();
				s.selectAll(mask);
				//rm.runCommand("Save", RIS.selectionPath);
				// clear mask image to content only within the (3D) selected area
				GetSelection3D.clearImageWithSelection(mask);
				
				rm.reset();
				*/
				String maskName = RIS.imageName + "_mask.zip";
				if (maskName.startsWith(";")) maskName = maskName.substring(1, maskName.length());
				
				if (pD.saveMask) {
					CheckAndSaveResult.saveMask(mask, resultDir + maskName);
				}
				
				
				// save runtime log
				long endTime = GetDateAndTime.getCurrentTimeInMs();
				String duration = GetDateAndTime.getDuration(endTime - startTime);
				/*
				try {
					if (!CheckAndSaveResult.saveLog(pD,RIS, thresholdBounds,pD.connectivity,date,time,duration,RIS.executionLogPath)) {
						IJ.log("   Current operation has not been logged.");
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
				*/
				IJ.run("Collect Garbage", "");
				IJ.log("  File: " + f.getName() + " processed, took " + String.valueOf((endTime-startTime)/1000) + " seconds.");
			} catch (NullPointerException e) {
				IJ.log("   !!Missing neccessary file(s) for the current operation!!");
				IJ.log("   File: " + f.getName() + " not processed properly.");
				e.printStackTrace();
			}
		}
		rm.setVisible(true);
		rm.close();
		System.gc(); // system garbage collector (release memory back to computer)	
	}
	
	public static void batchRun3 (
			File[] tifFileList
			) throws IOException, NullPointerException {
		
		ParameterDialog pD = new ParameterDialog();
		if (!pD.addDialog()) return;
		
		String resultDir = IJ.getDirectory("select result folder");
		if (null==resultDir) return;
		if (!resultDir.endsWith(File.separator)) resultDir += File.separator;

		String date = GetDateAndTime.getCurrentDate();
		String time = GetDateAndTime.getCurrentTime();
		IJ.log("\n\nRSOM vessel analysis batch processing executed at:");
		IJ.log(date + " " + time);
					
		// process through the file list			
		for (File f : tifFileList) {
			IJ.log("  Processing File: " + f.getCanonicalPath());
			long startTime = GetDateAndTime.getCurrentTimeInMs();

			ImagePlus inputImg = null;
			try {
				RsomImageStack RIS = new RsomImageStack(f.getCanonicalPath());
				inputImg = RIS.RSOMImg;
				
				ImagePlus RsomImg = inputImg.duplicate();
				
				// subtract background
				if (pD.doSubtractBackground) {
					BackgroundSubstraction bs = new BackgroundSubstraction();
					RsomImg = bs.subtractBackground(RsomImg,pD.bgRadius);
				}
				
				// save bg subtracted image to folder
				String bgSubtractedName = RIS.imageName + "_bgSubtracted.tif";
				CheckAndSaveResult.saveAsTiffStack(RsomImg, resultDir + bgSubtractedName);
				
				
				// save runtime log
				long endTime = GetDateAndTime.getCurrentTimeInMs();
				String duration = GetDateAndTime.getDuration(endTime - startTime);

				IJ.run("Collect Garbage", "");
				IJ.log("  File: " + f.getName() + " processed, took " + String.valueOf((endTime-startTime)/1000) + " seconds.");
			} catch (NullPointerException e) {
				IJ.log("   !!Missing neccessary file(s) for the current operation!!");
				IJ.log("   File: " + f.getName() + " not processed properly.");
				e.printStackTrace();
			}
		}
		System.gc(); // system garbage collector (release memory back to computer)	
	}
	
	public static void batchRunWithDifferentParameters (
			File[] tifFileList
			) throws IOException {
		// check ImageJ version
		if (IJ.versionLessThan("1.52f"))	{
			IJ.log("update ImageJ version");
			return;
		}
		
		ParameterDialog pD = new ParameterDialog();
		if (!pD.addDialogBatch()) return;

		String date = GetDateAndTime.getCurrentDate();
		String time = GetDateAndTime.getCurrentTime();
		IJ.log("\n\nRSOM vessel analysis batch processing executed at:");
		IJ.log(date + " " + time);
		IJ.log(" batch mode: generating mask with different parameter sets");
		// prepare RoiManager for batch processing
		// prepare RoiManager for operation
		RoiManager rm = RoiManager.getInstance2();
		if (rm == null) rm = new RoiManager();
		else rm.reset();
		rm.setVisible(false);
					
		// process through the file list			
		for (File f : tifFileList) {
			try {
				IJ.log("  Processing File: " + f.getName());
				long startTime = System.currentTimeMillis();

				//ImagePlus inputImg = null;
				RsomImageStack RIS = new RsomImageStack(f.getCanonicalPath());
				//inputImg = RIS.RSOMImg.duplicate();
				if (!pD.doNoSampleCode && !RIS.imageName.equals(RIS.sampleCode)) {
					continue;
				}
				
				boolean doRoi = pD.doRoi;
				if (!RIS.resultDirExist) {
					CheckAndSaveResult.makeResultPaths(RIS);
					IJ.log("   File: "+ f.getCanonicalPath() + " doesn't have result folder yet!");
					IJ.log("   An empty result folder has been created with the current operation, to store potential processing results.");
					if (doRoi) {
						IJ.log("   Therefore a manual ROI cannot be located. Processing for the current file will be on whole images instead.");
						doRoi = false;
					}
				}
				
				if (pD.doCalibration) {
					RsomImageStack.calibrateImage(RIS, pD.voxelSizeX, pD.voxelSizeY, pD.voxelSizeZ, pD.calUnit);
				}

				//ImagePlus RsomImg = inputImg.duplicate();
				rm.reset();
				int numResults = pD.methodSets.size();
				//ImagePlus RsomImg = inputImg.duplicate();
				// create batch files for multiple parameter sets
				
				for (int i=0; i<numResults; i++) {
					
					//methodSet currentMethod = pD.methodSets.get(i);
					ImagePlus RsomImg = RIS.RSOMImg.duplicate();
					System.out.println("method set " + String.valueOf(i) + ":");
					
					if (pD.methodSets.get(i).bgRadius!= 0) {
						BackgroundSubstraction bs = new BackgroundSubstraction();
						RsomImg = bs.subtractBackground(RsomImg, pD.methodSets.get(i).bgRadius);
					}
					
					//System.out.println("debug: bgRadius: " + String.valueOf(pD.methodSets.get(i).bgRadius));
					//System.out.println("debug: method: " + pD.methodSets.get(i).method);
					//System.out.println("debug: mdRadius: " + String.valueOf(pD.methodSets.get(i).mdRadius));
					
					//String debugPath2 = RIS.maskPath.replace("mask.zip", "RsomImg" + pD.methodSets.get(i).methodAlias +".tif");
					//FileSaver fs = new FileSaver(RsomImg);
					//fs.saveAsTiffStack(debugPath2);
					
					ThresholdImage t = new ThresholdImage();
					t.run(RsomImg, pD.methodSets.get(i).method, true);
					ImagePlus mask = t.mask;
					//double[] thresholdBounds = t.bounds;
					mask.setCalibration(RsomImg.getCalibration());
					
					//String debugPath = RIS.maskPath.replace("mask.zip", "debug_mask_" + pD.methodSets.get(i).methodAlias +".zip");
					//CheckAndSaveResult.saveMask(mask, debugPath);
					

					if (pD.methodSets.get(i).mdRadius != 0) {
						MedianFilter m = new MedianFilter();
						mask = m.medianFilter(mask, pD.methodSets.get(i).mdRadius);
					}
					/*
					GetSelection3D s = new GetSelection3D();
					if (pD.doRoi && new File(RIS.selectionPath).exists()) {
						IJ.redirectErrorMessages();
						rm.runCommand("Open",RIS.selectionPath);
					} else {
						s.selectAll(mask);
					}
					// clear mask image to content only within the (3D) selected area
					GetSelection3D.clearImageWithSelection(mask);
					*/
					
					rm.reset();
					mask.deleteRoi();
					mask.setTitle(RIS.sampleCode + "-mask: " + pD.methodSets.get(i).methodAlias);
					String newMaskPath = RIS.maskPath.replace("mask.zip", "mask_" + pD.methodSets.get(i).methodAlias +".zip");
					
					CheckAndSaveResult.saveMask(mask, newMaskPath);		
				}
				
				long endTime = System.currentTimeMillis();
				long duration = (endTime - startTime)/1000;
				
				IJ.log("  File: " + f.getName() + " processed, took " + String.valueOf((endTime-startTime)/1000) + " seconds.");
			} catch (NullPointerException e) {
				IJ.log("   !!Missing neccessary file(s) for the current operation!!");
				IJ.log("   File: " + f.getName() + " not processed properly.");
				e.printStackTrace();
			}
	 }
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
