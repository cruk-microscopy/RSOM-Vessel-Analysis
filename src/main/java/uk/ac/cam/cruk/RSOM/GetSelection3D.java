package uk.ac.cam.cruk.RSOM;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.gui.WaitForUserDialog;
import ij.io.RoiDecoder;
import ij.plugin.RoiInterpolator;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;

public class GetSelection3D {

	protected String roiPath;
	//protected RoiManager preROI;
	//protected RoiManager postROI;
	
	public Roi[] getSelection (String roiPath) {
		ArrayList<Roi> roiList = loadRoiList (roiPath);
		if (roiList==null || roiList.size()==0) return null;
		Roi[] roiArray = new Roi[roiList.size()];
		for (int i=0; i<roiList.size(); i++)
			roiArray[i] = roiList.get(i);
		return roiArray;
	}
	public ArrayList<Roi> loadRoiList (String path) {
		if ( path==null || !path.toLowerCase().endsWith(".zip") || !(new File(path)).exists() ) return null;
		ZipInputStream inStream = null;	ByteArrayOutputStream out = null;
        ArrayList<Roi> roiList = new ArrayList<Roi>();
        try {
            inStream = new ZipInputStream(new FileInputStream(path));
            byte[] buf = new byte[1024];
            int len;
            ZipEntry entry = inStream.getNextEntry();
            while (entry!=null) {
                String name = entry.getName();
                if (name.endsWith(".roi")) {
                    out = new ByteArrayOutputStream();
                    while ((len = inStream.read(buf)) > 0)
                        out.write(buf, 0, len);
                    out.close();
                    byte[] bytes = out.toByteArray();
                    RoiDecoder rd = new RoiDecoder(bytes, name);
                    Roi roi = rd.getRoi();
                    if (roi!=null) {
                        name = name.substring(0, name.length()-4);
                        roi.setName(name);
                        if (!roi.hasHyperStackPosition())
                        	roi.setPosition(0, 0, roi.getPosition());
                        roiList.add(roi);
                    }
                }
                entry = inStream.getNextEntry();
            }
            inStream.close();
        } catch (IOException e) {
            System.out.println("Load ROI fail: " + e);
        } finally {
            if (inStream!=null)
                try {inStream.close();} catch (IOException e) {}
            if (out!=null)
                try {out.close();} catch (IOException e) {}
        }
        return roiList;
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
	
	public static void clearImageWithSelection (
			ImagePlus imp,
			Roi[] rois
			) {
		int zStart = 1; int zEnd = imp.getNSlices();
		int zMin = zEnd; int zMax = zStart;
		for (int i=0; i<rois.length; i++) {
			int z = getRoiZPosition(rois[i]);
			if (z <= 0) continue;
			zMin = Math.min(zMin, z);
			zMax = Math.max(zMax, z);
			//imp.setPositionWithoutUpdate(0, z, 0);
			ImageProcessor ip = imp.getStack().getProcessor(z);
			ip.setValue(0);
			ip.fillOutside(rois[i]);
		}
		for (int z=zStart; z<=zEnd; z++) {
			if (z>=zMin && z<=zMax) continue;
			//println(z);
			ImageProcessor ip = imp.getStack().getProcessor(z);
			ip.setValue(0);
			ip.fill();
		}
	}
	public static int getRoiZPosition (Roi roi) {
		int posZ = roi.hasHyperStackPosition() ? roi.getZPosition() : roi.getPosition();
		if (posZ > 0 ) return posZ;
		// guess ROI z position from name (100-124-135, then 100)
		String name = roi.getName();
		int idx = name.indexOf("-");
		if (idx == -1) return 0;
		name = name.substring(0, idx);
		posZ = Integer.valueOf(name);
		if (posZ > 0) return posZ;
		// didn't find z position, return 0
		return 0;
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
