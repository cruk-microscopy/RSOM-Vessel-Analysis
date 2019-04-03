package uk.ac.cam.cruk.RSOM;

import ij.IJ;
import ij.ImagePlus;
import ij.process.*;
import ij.gui.*;
import ij.util.Tools;
import ij.io.*;
import ij.macro.Interpreter;
import ij.measure.Calibration;
import ij.plugin.FolderOpener;
import ij.plugin.PlugIn;

import java.awt.*;
import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

import org.apache.commons.io.FilenameUtils;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.prefs.DefaultPrefService;

import fiji.util.gui.GenericDialogPlus;

/** This plugin implements the File/Batch/Macro and File/Batch/Virtual Stack commands. */
public class BatchProcessor implements PlugIn {

	protected static String rootDirPath = "I:/research/seblab/data/shared_folders/light_microscopy/Emma_Brown/Ziqiang/New images";
	
	protected static String[] dataFormat = {"TIFF stack", "Image Sequence", "both"};
	protected static int format = 0;
	
	protected static String[] includes;
	protected static String[] excludes;
	
	protected static String[] operations = {
			"Clean Up Datasets",	//0
			"Vessel diameter analysis",	//1
			"Vessel diameter analysis with different parameters",	//2
			"Only generate mask, diameter, and object images",	//3
			"Get Analysis Result",	//4
			"Calibrate Images",	//5
			"Smooth 3D selection", //6
			"Create projection images"	//7
			};
	protected static int operation = 0;
	
	public static void batchRsomAnalysis (
			File[] fileArray
			) {
		
	}
	
	public static void cleanDatasets (
			File[] fileArray
			) throws IOException {
		// locate image sequence (filtered_dataset within a folder)
		// locate RSOM raw image folder and name
		// locate possible processed result folder
		// rename image sequence (get name from parent folder)
		// convert sequence to tiff and save
		// rename result folder and content inside
		
		
		//1 take a root dir
		String[] sampleCode = new String[fileArray.length];
		for (int i=0; i<fileArray.length; i++) {
			sampleCode[i] = getImageSequenceSampleCode(fileArray[i]);
		}
		
			//String[] includeWords,
				//String[] excludeWords
		//2 locate all image sequence inside
		for (int i=0; i<fileArray.length; i++) {
			if (isImageSequence(fileArray[i])) {
				String fileName = fileArray[i].getName();// get image sequence name
				if (fileName.contains("original"))	continue;	// skip original files
				String code = sampleCode[i];
				String resultPath = fileArray[i].getCanonicalPath() + "_result";
				File resultFolder = new File(resultPath);
				if (resultFolder.exists()) {	// result folder found, rename contained files and then the folder
					File[] listOfFiles = resultFolder.listFiles();
					for (int f=0; f<listOfFiles.length; f++) {
						String resultFileNameOld = listOfFiles[f].getName();
						String resultFileNameNew;
						if (resultFileNameOld.contains(fileName)) {
							resultFileNameNew = resultFileNameOld.replace(fileName, code);
						} else if (resultFileNameOld.startsWith("result")) {
							resultFileNameNew = resultFileNameOld.replace("result", code);
						} else resultFileNameNew = resultFileNameOld;
						//File newFile = new File(listOfFiles[f].getParent() + File.separator + resultFileNameNew);
						listOfFiles[f].renameTo(new File(listOfFiles[f].getParent() + File.separator + resultFileNameNew));
					}
					resultFolder.renameTo(new File(resultFolder.getParent() + File.separator + code + "_result"));
				}
				String tiffStackPath = fileArray[i].getParent() + File.separator + sampleCode[i] + ".tif";
				convertSequenceToTiffStack(fileArray[i].getCanonicalPath(), tiffStackPath);
			}
		}
		
		//2.5 open and convert all image sequence to tiff stack
		
		/*
		 * check first if result folder exist
		 * check if tiff stack exist
		 * remove any pre-exist tiff stacks (so bg-corrected should not be exist)
		 * calibrate image
		 * 
		 * 
		 * 
		 * 
		 */
		
		//3 get sample code for each image sequence
		//4 generate full path to save tiff image stack
	}
	
	public static void batchConvertImageSequenceToTiffStack(
			File[] imageSequences
			) {
		for (int i=0; i<imageSequences.length; i++) {
			File currentFile = imageSequences[i];
			if (!currentFile.isDirectory())	continue;
			if (!isImageSequence(currentFile))	continue;
			
			
		}
		
	}
	
	public static void batchCalibration () {
		
	}

	public static void batchPreProcessing () {
		
	}
	public static void batchGetResult () {
		
	}
	
	// a recursive function to get all files meeting criterion as List<File>
	public static List<File> getFileAsList (
			File rootDir,
			boolean checkSubDir,
			String[] includeWords,
			String[] excludeWords
			) {
		// if root dir is image sequence, return the root dir
		if (isImageSequence(rootDir)) {
			String name = rootDir.getName();
			boolean match = true;
			if (includeWords!=null) {
				for (String inc : includeWords) {
					if (!name.contains(inc.toLowerCase())) {
						match = false;
					}
				}
			}
			if (excludeWords!=null) {
				for (String exc : excludeWords) {
					if (name.contains(exc.toLowerCase())) {
						match = false;
					}
				}
			}
			if (match)	return Arrays.asList(rootDir);
			else return null;
		}
		
		// now go through files and folders inside root dir	
		List<File> fileList = new ArrayList<File>();

		File[] fileArray = rootDir.listFiles(new FilenameFilter() { // including all tif and tiff files
		    public boolean accept(File dir, String name) {
		    	String ext = FilenameUtils.getExtension(name);
				if (ext.equals("tif") || ext.equals("tiff")) {
					return true;
				} else {
					return false;
				}
		    }
		});
		
		for (int i=0; i<fileArray.length; i++) {
			if (fileArray[i].isDirectory()) {
				if (isImageSequence(fileArray[i])) {
					String name = fileArray[i].getName();
					boolean match = true;
					if (includeWords!=null) {
						for (String inc : includeWords) {
							if (!name.contains(inc.toLowerCase())) {
								match = false;
							}
						}
					}
					if (excludeWords!=null) {
						for (String exc : excludeWords) {
							if (name.contains(exc.toLowerCase())) {
								match = false;
							}
						}
					}
					if (match)	fileList.add(fileArray[i]);
				} else {
					if (checkSubDir) {
						fileList.addAll(getFileAsList(fileArray[i], checkSubDir, includeWords, excludeWords));
					}
				}
			} else { // we assume if it's not a directory then it's a file
				String ext = FilenameUtils.getExtension(fileArray[i].getName());
				if (ext.equals("tif") || ext.equals("tiff"))
					fileList.add(fileArray[i]);
			}
		}
		
		return fileList;
	}
	
	// a recursive function to get all image sequence as List<File>
	public static List<File> getTiffStackAsList (
			File rootDir,
			boolean checkSubDir,
			String[] includeWords,
			String[] excludeWords
			) {
		// if root dir is image sequence, return the root dir
		if (isImageSequence(rootDir)) {
			return null;
		}
		
		// now go through files and folders inside root dir	
		List<File> fileList = new ArrayList<File>();

		File[] fileArray = rootDir.listFiles(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				File f = null;
					try {
						f = new File(dir.getCanonicalPath() + File.separator + name);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				if (f==null) return false;	// if file not exist
				if (f.isDirectory())	{	// including subfolder excluding image sequence folder
					if (isImageSequence(f))	return false;
					else return true;
				}
				else {	// including all files with tif or tiff extension
					if (name.toLowerCase().endsWith(".tif") || name.toLowerCase().endsWith(".tiff")) {
						return true;
					} else {
						return false;
					}
				}
			}
		});
		
		for (int i=0; i<fileArray.length; i++) {
			boolean match = true;
			if (fileArray[i].isDirectory()) {
				if (isImageSequence(fileArray[i])) {
					continue;
				} else {
					if (checkSubDir) {
						fileList.addAll(getTiffStackAsList(fileArray[i], checkSubDir, includeWords, excludeWords));
					}
				}
			}
			if (fileArray[i].isFile() ) {
				String name = fileArray[i].getName().toLowerCase();
				if (includeWords!=null) {
					for (String inc : includeWords) {
						if (!name.contains(inc.toLowerCase())) {
							match = false;
						}
					}
				}
				if (excludeWords!=null) {
					for (String exc : excludeWords) {
						if (name.contains(exc.toLowerCase())) {
							match = false;
						}
					}
				}
				if (match)	fileList.add(fileArray[i]);
			}
		}
		return fileList;
	}
		
	
	// a recursive function to get all image sequence as List<File>
	public static List<File> getImageSequenceAsList (
			File rootDir,
			boolean checkSubDir,
			String[] includeWords,
			String[] excludeWords
			) {
		// if root dir is image sequence, return the root dir
		if (isImageSequence(rootDir)) {
			return Arrays.asList(rootDir);
		}
		
		// now go through files and folders inside root dir	
		List<File> fileList = new ArrayList<File>();

		File[] fileArray = rootDir.listFiles(new FilenameFilter() {	// get all sub-folders
			@Override
			public boolean accept(File dir, String name) {
				File f = null;
				try {
					f = new File(dir.getCanonicalPath() + File.separator + name);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				if (f == null) return false;	//not accept empty file
				if (f.isDirectory())	return true;	// accept all sub-folders
				else return false;	// reject all files
			}
		});
		
		
		for (int i=0; i<fileArray.length; i++) {
			boolean match = true;
			if (isImageSequence(fileArray[i])) { // if sub-folder is a image sequence folder
				//IJ.log("debug: getImageSequenceAsList: "+ fileArray[i].getName());
				String name = fileArray[i].getName().toLowerCase();
				if (includeWords.length != 0) {
					for (String inc : includeWords) {
						if (!name.contains(inc.toLowerCase())) {
							match = false;
						}
					}
				}
				if (excludeWords.length != 0) {
					for (String exc : excludeWords) {
						if (name.contains(exc.toLowerCase())) {
							match = false;
						}
					}
				}
				
				if (match) {
					fileList.add(fileArray[i]);
				}
			} else {	// if sub-folder is not a image sequence folder
				if (checkSubDir) {
					fileList.addAll(getImageSequenceAsList(fileArray[i], checkSubDir, includeWords, excludeWords));
				}
			}
		}
		return fileList;
	}
	
	// function to get all files meeting criterion as File[]
	public static File[] getFileAsArray (
			File rootDir,
			boolean checkSubDir,
			String[] includeWords,
			String[] excludeWords
			) {
		List<File> fileList = getFileAsList (rootDir, checkSubDir, includeWords, excludeWords);
	    return fileList.toArray(new File[fileList.size()]);
	}
	// function to get the paths of all files meeting criterion as String[]
	public static String[] getFilePathAsString (
			File rootDir,
			boolean checkSubDir,
			String[] includeWords,
			String[] excludeWords
			) throws IOException {
		List<File> fileList = getFileAsList (rootDir, checkSubDir, includeWords, excludeWords);
		File[] fileArray = fileList.toArray(new File[fileList.size()]);
		String[] paths = new String[fileArray.length];
		for (int i = 0; i < fileArray.length; i++) {
		   paths[i] = fileArray[i].getCanonicalPath();
		}
	    return paths;
	}
	// function to get the namess of all files meeting criterion as String[]
	public static String[] getFileNameAsString (
			File rootDir,
			boolean checkSubDir,
			String[] includeWords,
			String[] excludeWords
			) {
		List<File> fileList = getFileAsList (rootDir, checkSubDir, includeWords, excludeWords);
		File[] fileArray = fileList.toArray(new File[fileList.size()]);
		String[] paths = new String[fileArray.length];
		for (int i = 0; i < fileArray.length; i++) {
		   paths[i] = fileArray[i].getName();
		}
	    return paths;
	}
	
	public int[] findRISFiles(
			String[] inputStrArray
			) {
				
			List<Integer> maskIdxList = new ArrayList<Integer>();
	

			return null;
		}

	// function to check if a folder is image sequence
	public static boolean isImageSequence (
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
	
	// generate batch processing dialog
	public static boolean addDialog() {
		
		final Font highlightFont = new Font("Helvetica", Font.BOLD, 12);
		
		
		
		
		GenericDialogPlus gd = new GenericDialogPlus("get RSOM dataset");
		String fileOpenMessage = "Locate all RSOM dataset inside:";
		gd.addMessage(fileOpenMessage, highlightFont);
		gd.addDirectoryOrFileField("root folder", rootDirPath);

		
		gd.addChoice("data format:", dataFormat, dataFormat[format]);
		
		String nameFilterMessage = "Use  \",\"  to separate multiple keywords:";
		gd.addMessage(nameFilterMessage, highlightFont);
		gd.addStringField("including:", null);
		gd.addStringField("excluding:", null);
		String operationMessage = "Choose a batch operation command:";
		gd.addMessage(operationMessage, highlightFont);
		
		
		gd.addChoice("batch operation:", operations, operations[operation]);
		
		gd.showDialog();
		rootDirPath = gd.getNextString();
		format = gd.getNextChoiceIndex();
		
		includes = gd.getNextString().split(",");
		excludes = gd.getNextString().split(",");
		
		operation = gd.getNextChoiceIndex();
		
		
		
		if (gd.wasCanceled())	return false;
		return true;
	}	
	

		
	@Override
	public void run(String arg0) {

		//1 take a root dir
		if (!addDialog())	return;

		/*	Unsuccesful attempt to implement interactable dyanmic GUI (2019.03.26)
		FileList f = new FileList();
    	f.run(null);
    	
    	IJ.log(f.getRootPath());
    	IJ.log(f.getFilter());
		*/
		
		// parsing including and excluding keywords in file name
		List<String> inclist = new ArrayList<String>(Arrays.asList(includes));
		inclist.removeAll(Arrays.asList("", null));
		includes = inclist.toArray(new String[inclist.size()]);
		List<String> exclist = new ArrayList<String>(Arrays.asList(excludes));
		exclist.removeAll(Arrays.asList("", null));
		excludes = exclist.toArray(new String[exclist.size()]);
		
		// get selected root folder
		File rootDir = new File(rootDirPath);
		// prepare file list to store selected files
		List<File> files = new ArrayList<File>();
		// fill file list with selected file format
		switch (format) {
		case 0:
			files.addAll(getTiffStackAsList (rootDir, true, includes, excludes));
			break;
		case 1:
			files.addAll(getImageSequenceAsList (rootDir, true, includes, excludes));
			break;
		case 2:	//!!! getFileAsList somehow doesn't work right now 2019.03.26
			files.addAll(getTiffStackAsList (rootDir, true, includes, excludes));
			files.addAll(getImageSequenceAsList (rootDir, true, includes, excludes));
			break;
		}

		// generate a file array for later operations
		File[] fileArray = files.toArray(new File[files.size()]);

		// get selected file paths into a string list (with "\n" separation) for display and user confirmation
		ArrayList<String> pathOfSelectedFiles = new ArrayList<String>();
		for (int i=0; i<files.size(); i++) {
			try {
				pathOfSelectedFiles.add(files.get(i).getCanonicalPath() + "\n");
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		Collections.shuffle(pathOfSelectedFiles);
		pathOfSelectedFiles.add("\n " + "\n " + "   End Of File List." + "\n");
		int displayLength = 20;
		int rooling = 0;
		String displayFileList = "";
		boolean userNotConfirmed = true;
		boolean yesPressed = false;
		while (userNotConfirmed) {
			if (rooling<pathOfSelectedFiles.size()) {
				displayFileList = "";
			}
			for (int i=0; i<displayLength; i++) {
				if (rooling>=pathOfSelectedFiles.size())	break;
				else displayFileList += pathOfSelectedFiles.get(rooling);
				rooling++;
			}
			
			YesNoCancelDialog userConfirmDialog = new YesNoCancelDialog(IJ.getInstance(), "Selected files", displayFileList, "Proceed", "Display some other random files");
			if (userConfirmDialog.yesPressed()) {
				yesPressed = true;
				userNotConfirmed = false;
			}
			if (userConfirmDialog.cancelPressed()) {
				userNotConfirmed = false;
			}
		}
		if (!yesPressed) {	// get user confirmation to proceed
			return;
		}
		
		// Now we can do the batch operation based on the file list
		switch (operation) {
		case 0:	// Clean Up Datasets
			try {
				cleanDatasets(fileArray);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				IJ.log("error operating file!");
			}
			break;
		case 1:	// Vessel diameter analysis
			RSOM_Vessel_Analysis.batchRun(fileArray, 0);
			
			break;
		case 2:	// Vessel diameter analysis with different parameters
			
			break;
		case 3:	// Only generate mask, diameter, and object images
			RSOM_Vessel_Analysis.batchRun(fileArray, 1);
			
			break;
		case 4:	// Get Analysis Result
			
			break;
		case 5:	// Calibrate Images
			calibrateImages(fileArray);
			break;
		case 6:	// Smooth 3D selection
			
			break;
		case 7: // Create projection images
			
			break;
		}
		
		IJ.log("Batch processing completed");
	}
	
	public static void test (File[] fileList) {
		
	}
	
	public static String getImageSequenceSampleCode (
			File imageSequence
			) throws IOException {
		String fileName = imageSequence.getCanonicalPath();
		int beginIdx = fileName.lastIndexOf("R_");
		if (beginIdx == -1) return null;
		return fileName.substring(beginIdx,beginIdx+8);
	}
	
	public static void convertSequenceToTiffStack (
			String sequencePath,
			String tiffSavePath
			) {
		ImagePlus imp = FolderOpener.open(sequencePath,"file=tif");
		Calibration cal = imp.getCalibration();
		if (!cal.scaled()) {
			imp.getCalibration().pixelWidth = 20;
			imp.getCalibration().pixelHeight = 20;
			imp.getCalibration().pixelDepth = 4;
			imp.getCalibration().setUnit("micron");
		}	
		FileSaver fs = new FileSaver(imp);
		fs.saveAsTiffStack(tiffSavePath);
		imp.close();
		System.gc();
	}

	public static void calibrateImages (
			File[] tiffImageStackFiles
			) {
		for (File f : tiffImageStackFiles) {
			if (!f.exists()) continue;
			String filePath=null;
			try {
				filePath = f.getCanonicalPath();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				IJ.log("Can not open file: " + filePath);
				continue;
			}
			if (filePath.endsWith("tif") || filePath.endsWith("tiff")) {
				ImagePlus imp = IJ.openImage(filePath);
				Calibration cal = imp.getCalibration();
				if (!cal.scaled()) {
					imp.getCalibration().pixelWidth = 20;
					imp.getCalibration().pixelHeight = 20;
					imp.getCalibration().pixelDepth = 4;
					imp.getCalibration().setUnit("micron");
				}	
				FileSaver fs = new FileSaver(imp);
				fs.saveAsTiffStack(filePath);
				imp.close();
				System.gc();
			}
		}
	}
	
	public static void main(final String... args) throws Exception {


		String [] ij_args = 
			{ "-Dplugins.dir=C:/Fiji.app/plugins",
			"-Dmacros.dir=C:/Fiji.app/macros" };

		ij.ImageJ.main(ij_args);
		
		DefaultPrefService prefs = new DefaultPrefService();
		rootDirPath = prefs.get(String.class, "persistedString", rootDirPath);
		format = prefs.getInt(Integer.class, "persistedDouble", format);
		operation = prefs.getInt(Integer.class, "persistedDouble", operation);
		
		BatchProcessor bp = new BatchProcessor();
		bp.run(null);
		
		prefs.put(String.class, "persistedString", rootDirPath);
		prefs.put(Integer.class, "persistedDouble", format);
		prefs.put(Integer.class, "persistedDouble", operation);

	}


}