package uk.ac.cam.cruk.RSOM;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.gui.WaitForUserDialog;
import ij.plugin.RoiInterpolator;
import ij.plugin.frame.RoiManager;

public class GetSelection3D {

	protected String roiPath;
	protected RoiManager preROI;
	protected RoiManager postROI;
	
	public RoiManager getSelection (String roiPath) {
		
		RoiManager r = new RoiManager(true);
		if (roiPath != IJ.getDirectory("home") && preROI.runCommand("Open",roiPath)) {
			return r;
		} else {
			return getSelection();
		}
	}
	
	public RoiManager getSelection () {
		RoiManager r = new RoiManager();
		IJ.log("correct");
		return r;
	}

	public RoiManager interpolateROI (RoiManager r) {
		if (RoiManager.getInstance2() == null) {
			r = getSelection();
		}
		r.runCommand("Sort");
		RoiInterpolator ri = new RoiInterpolator();
		ri.run("");
		return r;
	}
	
	public void selectAll (
			ImagePlus inputImp
			) {
		if (inputImp == null) return;
		if (!inputImp.isStack()) {
			IJ.log("Input image: " + inputImp.getTitle() + " is not a stack.");
			return;
		}
		
		RoiManagerUtility.resetManager();
		RoiManager rm = RoiManager.getInstance2();
		//rm = new RoiManager(true);
		for (int i=1; i<inputImp.getNSlices()+1; i++) {
			inputImp.setPosition(i);
			Roi r = new Roi(0, 0, inputImp.getWidth(), inputImp.getHeight());
			r.setPosition(inputImp);
			rm.add(inputImp, r, -1);
			inputImp.deleteRoi();
		}
		return;
	}
	
	public RoiManager manualROI (
			ImagePlus inputImp,
			ImagePlus oriImp,
			String saveManualRoiPath
			) {
		inputImp.show();
		int xi = inputImp.getWindow().getBounds().x;
		int yi = inputImp.getWindow().getBounds().y;
		int wi = inputImp.getWindow().getBounds().width;
		RoiManager rm = RoiManager.getInstance();
		RoiManagerUtility.resetManager();
		if (RoiManagerUtility.isHidden()) RoiManagerUtility.showManager();
		rm.setLocation(xi+wi+10,yi+120);
		WaitForUserDialog w = new WaitForUserDialog("Selection3D","select and save to ROI Manager");
		w.setBounds(xi+wi+10,yi,250,100);
		w.show();
		// after user finished defining ROIs
		// hide RoiManager, paint them to original image as overlays
		RoiManagerUtility.hideManager();
		rm.runCommand("Save", saveManualRoiPath);
		rm.moveRoisToOverlay(oriImp);
		return rm;
	}
	
	public static void smoothCorners() {
		
	}
	
	public static void clearImageWithSelection (
			ImagePlus imp
			) {
		RoiManager rm = RoiManager.getInstance2();
		if (rm == null) return;
		if (rm.getCount() < 2) return;
		
		int x = imp.getWidth();
		int y = imp.getHeight();
		int z = imp.getNSlices();
		int c = imp.getNChannels();
		int t = imp.getNFrames();
		if ((c!=1) || (t!=1)) {
			IJ.log("Input image appears to be hyperstack, it might generate error in the end.");
		}
		
		int nROI = rm.getCount();
		rm.select(imp,0);
		int minRoiSlice = imp.getSlice();
		rm.select(imp,nROI-1);
		int maxRoiSlice = imp.getSlice();
		for (int i=1; i<=imp.getNSlices(); i++) {
			if (i<minRoiSlice || i>maxRoiSlice) {
				imp.setPosition(i);
				imp.getProcessor().fillRect(0, 0, x, y);
			}
		}
		for (int i=0; i<nROI; i++) {
			rm.select(imp, i);
			IJ.run(imp, "Clear Outside", "slice");
		}
		imp.deleteRoi();
	}
	
	public void run(
			ImagePlus imp,
			ImagePlus ori,
			String inputRoiPath,
			String savePath
			) {
		
		if (imp == null) return;
		if (!imp.isStack()) {
			IJ.log("Input image: " + imp.getTitle() + " is not a stack.");
			return;
		}
		
		
		
		RoiManagerUtility.resetManager();
		RoiManager rm = RoiManager.getInstance2(); //silent mode
		RoiManagerUtility.hideManager();
		
		if (inputRoiPath == null) {
			rm = manualROI(imp, ori, savePath);
		} else {
			IJ.redirectErrorMessages();
			rm.runCommand("Open",inputRoiPath);
			if (rm.getCount()<2) {	//	no or not enough ROI loaded, ask user for input;
				rm = manualROI(imp, ori, savePath);
			} else {
				rm.runCommand("Save", savePath);
			}
		}
		
		rm.runCommand(imp,"Sort");
		rm.runCommand(imp,"Interpolate ROIs");
		
		/*
		int nROI = rm.getCount();
		rm.select(imp,0);
		int minRoiSlice = imp.getSlice();
		rm.select(imp,nROI-1);
		int maxRoiSlice = imp.getSlice();
		for (int i=1; i<z+1; i++) {
			if (i<minRoiSlice || i>maxRoiSlice) {
				imp.setPosition(i);
				imp.getProcessor().fillRect(0, 0, x, y);
			}
		}
		for (int i=0; i<nROI; i++) {
			rm.select(imp, i);
			IJ.run(imp, "Clear Outside", "slice");
		}
		imp.deleteRoi();
		*/
		return;
	}
	
	public void main() {
		
	}
	
	
}
