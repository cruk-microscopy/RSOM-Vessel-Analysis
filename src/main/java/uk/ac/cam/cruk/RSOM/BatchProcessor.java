package uk.ac.cam.cruk.RSOM;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.*;
import ij.io.*;
import ij.measure.Calibration;
import ij.plugin.FolderOpener;
import ij.plugin.PlugIn;
import ij.plugin.frame.RoiManager;

import java.awt.*;
import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;

import org.apache.commons.io.FilenameUtils;
import org.scijava.prefs.DefaultPrefService;

import fiji.util.gui.GenericDialogPlus;

/** This plugin implements the Batch Processing options for RSOM image processing and analysis. */
public class BatchProcessor implements PlugIn {

	protected final static long JudgementDay = 1551398400000L;	// 2019.03.01 - 00:00:00
	protected static String rootDirPath = "I:/research/seblab/data/shared_folders/light_microscopy/Emma_Brown/Ziqiang/New images";
	
	protected static String[] dataFormat = {"TIFF stack", "Image Sequence", "both"};
	protected static int format = 0;
	
	protected static String[] includes;
	protected static String[] excludes;
	
	protected static String[] operations = {
			"Clean Up Datasets",	//0
			"Vessel diameter analysis: Complete process",	//1
			"Vessel diameter analysis: Generate mask",		//2
			"Vessel diameter analysis: Get Analysis Result",	//3
			"Vessel diameter analysis: Different parameters",	//4
			"Calibrate Images",	//5
			"Draw manual ROI",	//6
			"Smooth 3D selection", //7
			"Create projection images"	//8
			};
	protected static int operation = 0;
	
	public static void cleanDatasets (
			File[] fileArray
			) throws IOException {
		// locate image sequence (filtered_dataset within a folder)
		// locate RSOM raw image folder and name
		// locate possible processed result folder
		// rename image sequence (get name from parent folder)
		// convert sequence to tiff and save
		// rename result folder and content inside
		String messageTitle = "Clean UP Procedure";
		String messageBody = "Clean Up Procedure as of 2019.04.09\r\n" + " \n" +
				"* 1,   Convert image sequence to Tiff stack, rename with sample code;\r\n" + 
				"* 2,   Tiff stack will be without any processing, and uncalibrated;\r\n" + 
				"* 3,   Rename result folder and content inside;\r\n" + 
				"* 4,   for ...selection3D.zip created before 2019.03.01, rename it to ...manualROI.zip;\r\n" + 
				"* 5,   Report succeeded and failed entries in ImageJ log.";
		YesNoCancelDialog cleanMessage = new YesNoCancelDialog(IJ.getInstance(), messageTitle, messageBody);
		if (!cleanMessage.yesPressed()) {
			IJ.log("Batch clean up cancelled by User.");
			return;
		}
		
		//1 take a root dir
		String[] sampleCode = new String[fileArray.length];
		for (int i=0; i<fileArray.length; i++) {
			sampleCode[i] = getImageSequenceSampleCode(fileArray[i]);
		}
		ArrayList<File> alreadyCleanedFiles = new ArrayList<File>();
		ArrayList<File> cleanedFiles = new ArrayList<File>();
		ArrayList<File> notCleanedFiles = new ArrayList<File>();
		//String[] includeWords,
		//String[] excludeWords
		//2 locate all image sequence inside
		boolean alreadyCleaned = true;
		boolean renameResultFileSucceed = false;
		boolean renameResultDirSucceed = false;
		boolean removeOldTifSucceed = false;
		boolean saveNewTifSucceed = false;
		for (int i=0; i<fileArray.length; i++) {
			if (isImageSequence(fileArray[i])) {
				String fileName = fileArray[i].getName();// get image sequence name
				if (fileName.contains("original"))	continue;	// skip original files
				IJ.log("Cleaning dataset: " + fileArray[i].getCanonicalPath());
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
						
						if (resultFileNameNew.contains("selection3D.zip")) {
							BasicFileAttributes fatr = Files.readAttributes(listOfFiles[f].toPath(), BasicFileAttributes.class);
							// change selection3D to manualROI created before 2019.03.01
							if (fatr.creationTime().compareTo(FileTime.fromMillis(JudgementDay)) < 1) {
								resultFileNameNew = resultFileNameNew.replace("selection3D.zip", "manualROI.zip");
							}
						}
						File resultFileNew = new File(resultFileNameNew);
						if (!resultFileNew.exists())	{
							alreadyCleaned = false;
							renameResultFileSucceed = listOfFiles[f].renameTo(resultFileNew);
							if (!renameResultFileSucceed) {
								IJ.log("   Failed to rename result file: " + listOfFiles[f].getCanonicalPath());
							}
						} else {
							continue;
						}
					}
					File resultDirNew = new File(resultFolder.getParent() + File.separator + code + "_result");
					if (!resultDirNew.exists())	{
						alreadyCleaned = false;
						renameResultDirSucceed = resultFolder.renameTo(resultDirNew);
						if (!renameResultDirSucceed) {
							IJ.log("   Failed to rename result folder: " + resultFolder.getCanonicalPath());
						}
					}
				}
				String tiffStackPath = fileArray[i].getParent() + File.separator + sampleCode[i] + ".tif";
				File oldTiffStack = new File(fileArray[i].getParent() + File.separator + "filtered_dataset.tif");
				if (oldTiffStack.exists()) {
					alreadyCleaned = false;
					removeOldTifSucceed = oldTiffStack.delete();
					if (!removeOldTifSucceed) {
						IJ.log("   Failed to remove old Tiff stack: " + oldTiffStack.getCanonicalPath());
					}
				}
				File newTifStack = new File(tiffStackPath);
				if (!newTifStack.exists()) {
					alreadyCleaned = false;
					saveNewTifSucceed = convertSequenceToTiffStack(fileArray[i].getCanonicalPath(), tiffStackPath);
					if (!saveNewTifSucceed) {
						IJ.log("   Failed to save sample-coded Tiff stack: " + tiffStackPath);
					}
				}
			}
			if (alreadyCleaned) {
				alreadyCleanedFiles.add(fileArray[i]);
				IJ.log(" Dataset: " + fileArray[i].getCanonicalPath() + " had already been cleaned.");
			} else {
				if (renameResultFileSucceed && renameResultDirSucceed && removeOldTifSucceed && saveNewTifSucceed) {
					cleanedFiles.add(fileArray[i]);
					IJ.log(" Clean up completed.");
				} else {
					notCleanedFiles.add(fileArray[i]);
				}
			}
		}
		// create log window to summarize operation
		IJ.log("\nClean Up Summary:\n\nAlready cleaned datasets:");
		for (File f : alreadyCleanedFiles) {
			IJ.log("   " + f.getCanonicalPath());
		}
		IJ.log("\nCleaned datasets:");
		for (File f : cleanedFiles) {
			IJ.log("   " + f.getCanonicalPath());
		}
		IJ.log("\nNot cleaned datasets:");
		for (File f : notCleanedFiles) {
			IJ.log("   " + f.getCanonicalPath());
		}
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
				
			//List<Integer> maskIdxList = new ArrayList<Integer>();
	

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
		IJ.showStatus("   Locating RSOM image stacks...");
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
		IJ.showStatus("     Located " + String.valueOf(files.size()) + " images.");
		// get selected file paths into a string list (with "\n" separation) for display and user confirmation
		ArrayList<String> pathOfSelectedFiles = new ArrayList<String>();
		//pathOfSelectedFiles.add("   Begin Of File List." + "\n" + "\n");
		for (int i=0; i<files.size(); i++) {
			try {
				pathOfSelectedFiles.add(String.valueOf(i+1)+ ": " + files.get(i).getCanonicalPath() + "\n");
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		//Collections.shuffle(pathOfSelectedFiles);
		//pathOfSelectedFiles.add("\n " + "\n " + "   End Of File List." + "\n");
		final int displayLength = (int) (10 * (Math.floor(Toolkit.getDefaultToolkit().getScreenSize().getHeight()/200)-2));
		//IJ.log("displayLength: " + String.valueOf(displayLength));
		int pageLength = (int)(Math.ceil((double)pathOfSelectedFiles.size()/displayLength));
		int pageNumber = 0;
		String displayFileList = "";
		boolean userNotConfirmed = true;
		boolean yesPressed = false;
		while (userNotConfirmed) {
			displayFileList = "";
			pageNumber = pageNumber%pageLength;
			int i;
			for (i= displayLength*pageNumber; i<displayLength*(pageNumber+1); i++) {
				if (i >= pathOfSelectedFiles.size())	break;
				else displayFileList += pathOfSelectedFiles.get(i);
			}
			//YesNoCancelDialog userConfirmDialog = new YesNoCancelDialog(IJ.getInstance(), "Selected files", displayFileList, "Proceed", "Display some other random files");
			String dialogTitle = String.valueOf(displayLength*pageNumber+1) + " ~ " + String.valueOf(i) + " of total " + String.valueOf(files.size()) + " selected files";
			pageNumber++;
			YesNoCancelDialog userConfirmDialog = new YesNoCancelDialog(IJ.getInstance(), dialogTitle, displayFileList, "Proceed", "Next...");
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
			/* clean up procedure:	as of 2019.04.09
			 * 1,	rename sequence with sample code
			 * 2,	convert sequence to tiff
			 * 3, 	rename result folder and content inside
			 * 4,	for selection3D.zip created before 2019.03.28, rename it to manualROI.zip
			 * 5,	report succeeded and failed datasets in ImageJ log
			 */
			try {
				cleanDatasets(fileArray);
			} catch (IOException e) {
				IJ.log(" Error opening/editing file!");
			}
			break;
		case 1:	// Vessel diameter analysis: Complete process
			try {
				RSOM_Vessel_Analysis.batchRun(fileArray, 1);
			} catch (IOException e) {
				e.printStackTrace();
			}
			break;
		case 2:	// Vessel diameter analysis: Generate mask
			try {
				RSOM_Vessel_Analysis.batchRun(fileArray, 2);
			} catch (IOException e) {
				e.printStackTrace();
			}
			break;
		case 3:	// Vessel diameter analysis: Get Analysis Result
			try {
				RSOM_Vessel_Analysis.batchRun(fileArray, 3);
			} catch (IOException e) {
				e.printStackTrace();
			}
			break;
		case 4:	// Vessel diameter analysis: Different parameters
			try {
				RSOM_Vessel_Analysis.batchRunWithDifferentParameters(fileArray);
			} catch (IOException e) {
				e.printStackTrace();
			}
			break;
		case 5:	// Calibrate Images
			calibrateImages(fileArray);
			break;
		case 6:	// Draw Manual ROI
			try {
				drawManaulRoi(fileArray);
			} catch (IOException e) {
				e.printStackTrace();
			}
			break;
		case 7:	// Smooth 3D selection
			try {
				SmoothSelection3D.batchRun(fileArray);
			} catch (IOException e) {
				e.printStackTrace();
			}
			break;
		case 8: // Create projection images
			try {
				CreateProjectionImages.batchRun(fileArray);
			} catch (IOException e) {
				e.printStackTrace();
			}
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
		int timeCodeBeginIdx = fileName.lastIndexOf("R_");
		if (timeCodeBeginIdx == -1) return null;

		int dateCodeBeginIdx = fileName.indexOf("201");
		if (dateCodeBeginIdx == -1) return null;
		
		String timeCode = fileName.substring(timeCodeBeginIdx+2,timeCodeBeginIdx+8);
		String dateCode = fileName.substring(dateCodeBeginIdx,dateCodeBeginIdx+8);
		
		return ("R_"+timeCode+"_"+dateCode);
		
	}
	
	public static boolean convertSequenceToTiffStack (
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
		Boolean succeed = fs.saveAsTiffStack(tiffSavePath);
		imp.close();
		System.gc();
		return succeed;
	}

	public static void calibrateImages (
			File[] tiffImageStackFiles
			) {
		// get user input dialog
		GenericDialogPlus gd = new GenericDialogPlus("Calibrate images");
		gd.setInsets(0,0,0);
		gd.addNumericField("X:", 20, 1);
		gd.setInsets(0,0,0);
		gd.addNumericField("Y:", 20, 1);
		gd.setInsets(0,0,0);
		gd.addNumericField("Z:", 4, 1);
		gd.setInsets(0,0,0);
		gd.addStringField("Unit:", "micron");
		gd.showDialog();
		//boolean doNoSampleCode = gd.getNextBoolean();
		double voxelSizeX = gd.getNextNumber();
		double voxelSizeY = gd.getNextNumber();
		double voxelSizeZ = gd.getNextNumber();
		String calUnit = gd.getNextString();
		if (gd.wasCanceled())	return;
		
		for (File f : tiffImageStackFiles) {
			if (!f.exists()) continue;
			String filePath=null;
			try {
				filePath = f.getCanonicalPath();
			} catch (IOException e) {
				IJ.log("Can not open file: " + filePath);
				continue;
			}
			IJ.log("Calibrating image file: " + filePath);
			if (filePath.endsWith("tif") || filePath.endsWith("tiff")) {
				ImagePlus imp = IJ.openImage(filePath);
				Calibration cal = imp.getCalibration();
				if (!cal.scaled()) {
					imp.getCalibration().pixelWidth = voxelSizeX;
					imp.getCalibration().pixelHeight = voxelSizeY;
					imp.getCalibration().pixelDepth = voxelSizeZ;
					imp.getCalibration().setUnit(calUnit);
				}	
				FileSaver fs = new FileSaver(imp);
				fs.saveAsTiffStack(filePath);
				imp.close();
				System.gc();
				IJ.log("   Calibration completed.");
			}
		}
	}
	
	public static void drawManaulRoi (
			File[] fileArray
			) throws IOException {
		RoiManager rm = RoiManager.getInstance2();
		if (rm == null) rm  = new RoiManager();
		else rm.reset();
		rm.setVisible(true);
		
		ImagePlus ori = null;
		ImagePlus mask = null;
		for (File f : fileArray) {
			rm.setVisible(false);
			rm.reset();
			RsomImageStack RIS = new RsomImageStack(f.getCanonicalPath());
			ori = RIS.RSOMImg;
			if (ori == null) {
				IJ.log(" RSOM image of File: " + f.getCanonicalPath() + " can not be located (skip).");
				continue;
			}
			if (!RIS.resultDirExist) {
				CheckAndSaveResult.makeResultPaths(RIS);
				IJ.log("  File: "+ f.getCanonicalPath() + " doesn't have result folder yet.");
				
				IJ.log("   An empty result folder has been created with the current operation.");
			} else {
				if (new File(RIS.maskPath).exists()) {
					mask = IJ.openImage(RIS.maskPath);
				} else {
					IJ.log("   Mask image can not be located.");
				}
				if (new File(RIS.manualROIPath).exists()) {
					rm.runCommand("Open", RIS.manualROIPath);
				}
			}
			IJ.run(ori, "Enhance Contrast", "saturated=0.35");
			//IJ.run(ori, "Set... ", "zoom=200");
			ori.show();
			ori.getWindow().setLocationAndSize(100, 250, ori.getWidth(), ori.getHeight());
			
			if (mask != null) {
				//IJ.run(mask, "Set... ", "zoom=200"); 
				mask.show();
				mask.getWindow().setLocationAndSize(150 + ori.getWidth(), 250, ori.getHeight(), ori.getHeight());
				
				rm.setLocation(200 + ori.getWidth()*2, 400);
				IJ.run("Synchronize Windows", "");
			} else {
				rm.setLocation(150 + ori.getWidth(), 400);
			}
			rm.setVisible(true);
			
			WaitForUserDialog w = new WaitForUserDialog("Selection3D","select and save to ROI Manager\nwhen finished press OK.");
			w.setBounds(rm.getLocation().x, 250, 400, 100);
			w.show();
			if (w.escPressed())
			rm.runCommand("Save", RIS.manualROIPath);
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