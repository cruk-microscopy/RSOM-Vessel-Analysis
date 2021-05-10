package uk.ac.cam.cruk.RSOM;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.FilenameUtils;
import org.scijava.prefs.DefaultPrefService;

import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.ImagePlus;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.process.StackStatistics;

public class getStackStatistics implements PlugIn {

	protected String rootDirPath = "";
	protected String includeString = "";
	protected String excludeString = "";
	protected static String[] includes;
	protected static String[] excludes;
	
	@Override
	public void run(String arg) {
		
		DefaultPrefService prefs = new DefaultPrefService();
		rootDirPath = prefs.get(String.class, "RSOM-temp-rootDirPath", rootDirPath);
		includeString = prefs.get(String.class, "RSOM-temp-includeString", includeString);
		
		GenericDialogPlus gd = new GenericDialogPlus("Get Selected Files");
		gd.addDirectoryOrFileField("input folder", rootDirPath, 70);
		String nameFilterMessage = "Use  \",\"  to separate multiple keywords:";
		gd.addMessage(nameFilterMessage);
		gd.addStringField("including:", includeString, 30);
		gd.addCheckbox("save to file", false);
		gd.addDirectoryOrFileField("save path", "", 70);
		gd.showDialog();
		if (gd.wasCanceled()) return;
		rootDirPath = gd.getNextString();		
		includeString = gd.getNextString();
		includes = includeString.split(",");
		//excludeString = gd.getNextString();
		//excludes = excludeString.split(",");
		boolean saveToFile = gd.getNextBoolean();
		String savePath = gd.getNextString();
		
		prefs.put(String.class, "RSOM-temp-rootDirPath", rootDirPath);
		prefs.put(String.class, "RSOM-temp-includeString", includeString);
		// get file list
		ArrayList<File> fileList = getFileAsList(new File(rootDirPath), true, includes, excludes);
		
		ResultsTable result = getImageStats(fileList);
		
		result.show("image information");
		if (saveToFile) {
			try {
				if (!savePath.endsWith(".csv")) savePath += ".csv";
				result.saveAs(savePath);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
	}
	
	// get image statistics
	public ResultsTable getImageStats (ArrayList<File> fileList) {
		
		ResultsTable rt = new ResultsTable();
		rt.setPrecision(3);
		// results: file path, file format, mean, total pixel count, volume (20*20*4 µm^3), min, max, stdDev
		for (File file : fileList) {
			String path = file.getAbsolutePath();
			ImagePlus imp = IJ.openImage(path);
			if (imp==null) continue;
			imp.setCalibration(null);
			int type = imp.getType();
			String imageType = "";
			switch (type) {
			case ImagePlus.GRAY8:
				imageType = "8-bit";
				break;
			case ImagePlus.GRAY16:
				imageType = "16-bit";
				break;
			case ImagePlus.GRAY32:
				imageType = "32-bit";
				break;
			default:
				continue;
			}
			
			StackStatistics stats = new StackStatistics(imp);
			long[] histogram = stats.getHistogram();
			
			
			double mean = stats.mean;
			double min = stats.min;
			double max = stats.max;
			double stdDev = stats.stdDev;
			//double sum = mean * stats.pixelCount;
			long signalCount = stats.pixelCount - histogram[0];
			double volume = signalCount* 20 * 20 * 4;
			if (signalCount==histogram[255]) imageType = "binary";
			
			rt.incrementCounter();
			rt.addValue("file path", path);
			rt.addValue("image type", imageType);
			rt.addValue("total signal pixel count", signalCount);
			rt.addValue("signal volume ratio (%)", (double)signalCount*100/(double)stats.pixelCount);
			rt.addValue("total signal volume (20*20*4 µm^3)", volume);
			rt.addValue("mean", mean);
			rt.addValue("standard deviation", stdDev);
			rt.addValue("min", min);
			rt.addValue("max", max);
		}
		
		return rt;
		
		
	}
	
	// a recursive function to get all files meeting criterion as List<File>
	public static ArrayList<File> getFileAsList (
			File rootDir,
			boolean checkSubDir,
			String[] includeWords,
			String[] excludeWords
			) {
		
		if (!rootDir.exists()) return null;
		// go through files and folders inside root dir	
		ArrayList<File> fileList = new ArrayList<File>();
		
		if (rootDir.isFile()) {
			boolean match = true;
			String name = rootDir.getName();
			if (includeWords!=null) {
				for (String inc : includeWords) {
					if (!name.contains(inc.toLowerCase())) {
						match = false;
					}
				}
			}
			/*
			if (excludeWords!=null) {
				for (String exc : excludeWords) {
					if (name.contains(exc.toLowerCase())) {
						match = false;
					}
				}
			}
			*/
			if (match) {
				fileList.add(rootDir);
				return fileList;
			}
		}
		
		
		File[] fileArray = rootDir.listFiles(new FilenameFilter() { // including all tif and tiff files
		    public boolean accept(File dir, String name) {
		    	for (String inc : includeWords) {
					if (name.contains(inc)) {
						return true;
					}
				}
		    	return false;

		    }
		});
		fileList = new ArrayList<File>(Arrays.asList(fileArray));

		return fileList;
	}

}
