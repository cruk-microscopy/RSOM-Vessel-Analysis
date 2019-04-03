package uk.ac.cam.cruk.RSOM;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import ij.IJ;
import ij.ImageJ;
import ij.measure.ResultsTable;

import java.io.BufferedReader;
import java.io.File;
//import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;




public class ReadAndWriteCsv {

	protected static ResultsTable rt;
	protected static String rs;
	protected static String resultPath;
	protected static Boolean overwrite = false;
	protected static Boolean append = true;
	
	
	public static String[] readCsvFileToArray (
			String csvFilePath
			) {
		
        String row = "";
        String[] output = null;

        try (BufferedReader br = new BufferedReader(new FileReader(csvFilePath))) {

            while ((row = br.readLine()) != null) {
                // use comma as separator
            	output = row.split(",");
                //System.out.println("Country [code= " + country[4] + " , name=" + country[5] + "]");
            }
            return output;
        } catch (IOException e) {
            e.printStackTrace();
        }
		return null;
	}
	
	public static int searchCsvForEntry(
			String csvFilePath,
			int searchColumnIndex,
			String searchString
			) throws IOException {
		
		int foundValue = 0;
		
		try { 
			BufferedReader br = new BufferedReader(new FileReader(csvFilePath));
			String line;
		    while ( (line = br.readLine()) != null ) {
		        String[] values = line.split(",");
		        if(values[searchColumnIndex].equals(searchString)) {
		        	foundValue = Integer.valueOf(values[searchColumnIndex+1]);
		            break;
		        }
		    }
		    br.close();
		} catch (IOException e) {
			return 1;
		}
	    return foundValue+1;

	}
	
	
	public static boolean resultsTableToCsvFile (
			ResultsTable rt,
			String resultPath
			) {
		if (!validateSavingPath(resultPath, overwrite, append)) {
			IJ.log("Result is not saved.");
			return false;
		}
		try {
			rt.saveAs(resultPath);
			return true;
		} catch (IOException e) {
			e.printStackTrace();
			IJ.log("Result saving error.");
		}
		return false;
	}
	
	public static boolean writeToResultTable (
			String resultString,
			ResultsTable rt,
			Boolean updateDisplay
			) {
		if (!validateSavingPath(resultPath, overwrite, append)) {
			IJ.log("Result is not saved.");
			return false;
		}
		
		//rt= ResultsTable.open2(csvPath);
		
		//rt.addValue(String column, double value);
		
		return false;
		
	}
	
	
	
	public static boolean writeToCsvFile (
			String resultString,
			String resultPath,
			Boolean overwrite,
			Boolean append
			) {
		if (!validateSavingPath(resultPath, overwrite, append)) {
			IJ.log("Result is not saved.");
			return false;
		}
		
		
		// test append mode
		/*
		for (int r = 0; r < overlay.size(); r++) {
			// if current ROI in the analysis region
			if (roiCheck[r]) {
				// increment the output table
				rt.incrementCounter();
				// add the file name
				rt.addValue("Name", rawDataDirList[i]);
				// add all measures statistics
				rt.addValue("Area", roiAreas[r]);
				for (int c = 0; c < rawSeriesImp.getNChannels(); c++) {
					rt.addValue("Channel" + (c + 1) + " mean intensity", roiMeans[c][r]);
					rt.addValue("Channel" + (c + 1) + " std", roiStds[c][r]);
				}
				rt.addValue("Edge dapi spots", roiSpotCountsAll[r] - roiSpotCountsCenter[r]);
				rt.addValue("Center dapi spots", roiSpotCountsCenter[r]);
			}
		}
		
		*/
		
		return false;
	}
	
	public static boolean saveToCsv (
			ResultsTable rt,
			String resultPath,
			Boolean overwrite,
			Boolean append
			) {
		
		if (!validateSavingPath(resultPath, overwrite, append)) {
			IJ.log("Result is not saved.");
			return false;
		}
		
		// test append mode
		try {
			rt.saveAs(resultPath);
			return true;
		} catch (IOException e) {
			e.printStackTrace();
			IJ.log("Result saving error.");
		}
		return false;
	}
	
	public static boolean validateSavingPath (
			String resultPath,
			Boolean overwrite,
			Boolean append
			) {	
		File resultFile = new File(resultPath);
		if (resultFile.isDirectory()) {
			IJ.log("Result saving path appear to be a directory:");
			IJ.log(resultPath);
			return false;
		}
		if (!resultFile.exists()) {
			try {
				resultFile.createNewFile();
				IJ.log("Result file created:");
				IJ.log(resultPath);
				return true;
			} catch (IOException e) {
				e.printStackTrace();
				return false;
			}
		} else {
			if (overwrite) {
				resultFile.delete();
				resultFile = new File(resultPath);
				return true;
			}
			if (append) {
				return true;
			}
			IJ.log("Result file already exist, either overwrite or append to it.");
			return false;
		}
	}
	
	
	public void run(String args[]) {
		String fileName = "C:/workspace/filtered_dataset_result/java_test.xlsx";
		String cellValue = "row_1, col_1";
	
		XSSFWorkbook workbook = new XSSFWorkbook();
		XSSFSheet sheet = workbook.createSheet("Datatypes in Java");
	
		Row row = sheet.createRow(0);
		int colNum = 0;
		Cell cell = row.createCell(0);
		cell.setCellValue((String)cellValue);       
		try {
			FileOutputStream outputStream = new FileOutputStream(fileName);
			workbook.write(outputStream);
			workbook.close();
			System.out.println("done.");
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void main() {
		
		/*
		if (IJ.versionLessThan("1.46f"))	System.exit(0);

		String [] ij_args = 
			{ "-Dplugins.dir=C:/Fiji.app/plugins",
			"-Dmacros.dir=C:/Fiji.app/macros" };
		ImageJ.main(ij_args);
		*/
		
		ReadAndWriteCsv rawc = new ReadAndWriteCsv();
		rawc.run(null);
	}
}
