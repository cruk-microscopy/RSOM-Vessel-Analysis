package uk.ac.cam.cruk.RSOM;

import java.util.ArrayList;
import java.util.Arrays;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.text.DecimalFormat;

import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;

import org.scijava.prefs.DefaultPrefService;
import org.scijava.vecmath.Color3f;

import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.GUI;
import ij.gui.GenericDialog;
import ij.gui.NewImage;
import ij.gui.Roi;
import ij.io.OpenDialog;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.plugin.RGBStackMerge;
import ij.plugin.RoiEnlarger;
import ij.plugin.filter.ThresholdToSelection;
import ij.plugin.frame.PlugInFrame;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import ij.process.LUT;
import ij3d.Image3DUniverse;

public class BatchROIShrink implements PlugIn {
	
	// target image, ROI Manager, loaded ROIs
	protected ImagePlus targetImage;
	protected RoiManager currentRm;
	protected Roi[] rmOrigRois;
	protected Roi[] initialRois;
	protected Roi[] shrinkedRois;
	protected String selectedSize = "0 µm";
	protected String[] shrinkSizes = {selectedSize};
	
	// parameters for batch ROI shrink
	protected boolean loadImage = false;
	protected String imagePath = "";
	protected boolean loadSelection3D = true;
	protected String roiPath = "";
	protected double shrinkStep = 20.0; // shrink each time 20 µm from all direction 
	protected double pixelSize = 20.0; // default calibration is 20µm/pixel
	protected String saveDirPath = "";
	
	// parameter for display shrinked ROIs
	protected final String[] optionColor = {"glasbey on dark", "glasbey inverted", 
			"16 colors", "brgbcmyw", "6 shades", "5 ramps", "thal", "smart"};
	protected final String[] option3D = {"no 3D view", "Volume", "Orthoslice"};
	protected int idxOptionColor = 0;
	protected int idxOption3D = 0;
	protected boolean roiDoCalibrate = false;
	//protected ImagePlus vesselMaskImage = null;
	//protected ImagePlus shrinkedRoiImage = null;
	protected ImagePlus overlayComposite = null;

	// parameters for GUI
	protected PlugInFrame pf;
	protected final int lineWidth = 40;
	protected final Color panelColor = new Color(204, 229, 255);
	protected final Font textFont = new Font("Helvetica", Font.PLAIN, 12);
	protected final Color fontColor = Color.BLACK;
	protected final Font errorFont = new Font("Helvetica", Font.BOLD, 12);
	protected final Color errorFontColor = Color.RED;
	protected final Color textAreaColor = new Color(204, 229 , 255);
	protected final Font panelTitleFont = new Font("Helvetica", Font.BOLD, 13);
	protected final Color panelTitleColor = Color.BLUE;
	protected final EmptyBorder border = new EmptyBorder(new Insets(5, 5, 5, 5));
	protected final Dimension textAreaMax = new Dimension(260, 150);
	protected final Dimension tablePreferred = new Dimension(260, 100);
	protected final Dimension tableMax = new Dimension(260, 150);
	protected final Dimension panelTitleMax = new Dimension(500, 30);
	protected final Dimension panelParentSize =new Dimension(320, 300);
	protected final Dimension panelContentSize =new Dimension(300, 300);
	protected final Dimension panelMax = new Dimension(300, 500);
	protected final Dimension panelMin = new Dimension(300, 200);
	protected final Dimension buttonSize = new Dimension(90, 10);
	
	protected JTextArea sourceInfo;
	protected JComboBox<String> shrinkSizeSelection;
	protected JButton btnBatchShrink;
	protected JButton btnRefresh;
	protected JButton btnLoadRoi;
	protected JButton btnShowRoi;
	protected JButton btnShowOverlay;
	protected JButton btnConvertMask;
	
	protected JButton btnRestoreManager;

	
	public void addPanelToFrame (
			Frame f
			) {
		
		// create a parent panel for both title and content panels
		JPanel parentPanel = new JPanel();
		parentPanel.setBorder(border);
		parentPanel.setBackground(f.getBackground());
		parentPanel.setLayout(new BoxLayout(parentPanel, BoxLayout.Y_AXIS));
		//parentPanel.add(titlePanel, BorderLayout.NORTH);
		
		// create and configure the content panel
		JPanel contentPanel = new JPanel();
		contentPanel.setBorder(border);
		contentPanel.setBackground(panelColor);
		contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
		
		// create and configure the title panel "Sample image and ROIs"
		JLabel title = new JLabel("Image Info");
		title.setFont(panelTitleFont);
		title.setForeground(panelTitleColor);
		contentPanel.add(title);
		title.setAlignmentX(Component.CENTER_ALIGNMENT);
		
		sourceInfo = new JTextArea();
		sourceInfo.setMaximumSize(textAreaMax);
		sourceInfo.setEditable(false);
		contentPanel.add(sourceInfo);
		sourceInfo.setAlignmentX(Component.CENTER_ALIGNMENT);
		
		//contentPanel.add(btnBatchShrink);
		//btnBatchShrink.setAlignmentX(Component.CENTER_ALIGNMENT);
		shrinkSizeSelection = new JComboBox<String>(shrinkSizes);
		shrinkSizeSelection.setSelectedIndex(0);
		shrinkSizeSelection.setMaximumSize(new Dimension(150, 30));
		shrinkSizeSelection.addActionListener(new ActionListener() {
		    @Override
		    public void actionPerformed(ActionEvent event) {
		    	selectedSize = (String) shrinkSizeSelection.getSelectedItem();
		    }
		});
		contentPanel.add(shrinkSizeSelection);
		shrinkSizeSelection.setAlignmentX(Component.CENTER_ALIGNMENT);
		
		
		JPanel buttonPanel = new JPanel();
		btnBatchShrink = new JButton("batch shrink");
		btnRefresh = new JButton("refresh");
		btnLoadRoi = new JButton("load ROIset");
		btnShowRoi = new JButton("show ROI");
		btnShowOverlay = new JButton("show overlay");
		btnConvertMask = new JButton("to mask");
		btnRestoreManager = new JButton("restore ROI Manager");
		

		GroupLayout buttonLayout = new GroupLayout(buttonPanel);
		buttonPanel.setLayout(buttonLayout);
		buttonLayout.setAutoCreateGaps(true);
		buttonLayout.setAutoCreateContainerGaps(true);

		buttonLayout.setHorizontalGroup(buttonLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
			 .addGroup(buttonLayout.createSequentialGroup()
				.addComponent(btnBatchShrink)
			    .addComponent(btnRefresh))
			 .addGroup(buttonLayout.createSequentialGroup()
				.addComponent(btnLoadRoi)
				.addComponent(btnShowRoi))
			 .addGroup(buttonLayout.createSequentialGroup()
				.addComponent(btnShowOverlay)
			    .addComponent(btnConvertMask))
			 .addGroup(buttonLayout.createSequentialGroup()
			    .addComponent(btnRestoreManager)));
			 	
		buttonLayout.linkSize(SwingConstants.HORIZONTAL, 
				btnBatchShrink, btnRefresh, btnShowRoi, btnLoadRoi, btnShowOverlay, btnConvertMask);	

		buttonLayout.setVerticalGroup(buttonLayout.createSequentialGroup()
			.addGroup(buttonLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
				.addGroup(buttonLayout.createSequentialGroup()
		                .addComponent(btnBatchShrink)
		                .addComponent(btnLoadRoi)
		                .addComponent(btnShowOverlay)
		                .addComponent(btnRestoreManager))
				.addGroup(buttonLayout.createSequentialGroup()
		                .addComponent(btnRefresh)
		                .addComponent(btnShowRoi)
		                .addComponent(btnConvertMask))));
		
		//buttonPanel.setBorder(border);
		buttonPanel.setBackground(panelColor);
		contentPanel.add(buttonPanel);
		buttonPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
		
		contentPanel.setPreferredSize(panelContentSize);
		contentPanel.setMaximumSize(panelMax);
		
		parentPanel.add(contentPanel);
		contentPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
		parentPanel.setPreferredSize(panelParentSize);
		
		// assign actions to components
		sourceInfo.setText(getImageAndROIInfo());
		if (targetImage==null) {
			sourceInfo.setFont(errorFont);
			sourceInfo.setForeground(errorFontColor);
		} else {
			sourceInfo.setFont(textFont);
			sourceInfo.setForeground(fontColor);
		}
		// configure batch shrink button
		btnBatchShrink.addActionListener(new ActionListener() { 
		  @Override 
		  public void actionPerformed(ActionEvent ae) {batchShrink();}
		});
		// configure refresh button
		btnRefresh.addActionListener(new ActionListener() { 
		  @Override 
		  public void actionPerformed(ActionEvent ae) {refresh();}
		});
		// configure load batch ROI button
		btnLoadRoi.addActionListener(new ActionListener() { 
		  @Override 
		  public void actionPerformed(ActionEvent ae) {loadBatchRoi(null);}
		});
		// configure show ROI button
		btnShowRoi.addActionListener(new ActionListener() { 
		  @Override 
		  public void actionPerformed(ActionEvent ae) {showRoi();}
		});
		// configure show Overlay button
		btnShowOverlay.addActionListener(new ActionListener() { 
		  @Override 
		  public void actionPerformed(ActionEvent ae) {showOverlay();}
		});
		// configure  convert to mask button
		btnConvertMask.addActionListener(new ActionListener() { 
		  @Override 
		  public void actionPerformed(ActionEvent ae) {convertToMask();}
		});
		// configure restore ROI Manager button
		btnRestoreManager.addActionListener(new ActionListener() { 
		  @Override 
		  public void actionPerformed(ActionEvent ae) {restoreManager();}
		});
		
		
		// add title and content panel to the parent panel, and finally add to plugin frame
		parentPanel.add(contentPanel);
		contentPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
		
		//f.add(parentPanel, BorderLayout.NORTH);
		f.add(parentPanel);
		f.pack();
		//f.showDialog();
	}
	
	/*
	 * 	Functions for panel component actions:
	 */
		// function to refresh the source image and loaded ROIs
		public void refresh() {
			targetImage = WindowManager.getCurrentImage();
			currentRm = RoiManager.getInstance();
			/*
			shrinkedRois = null;
			selectedSize = "0 µm";
			shrinkSizes = new String[]{selectedSize};
			*/
			String info = getImageAndROIInfo();
			sourceInfo.setText(info);
			if (targetImage==null) {
				sourceInfo.setFont(errorFont);
				sourceInfo.setForeground(errorFontColor);
			} else {
				sourceInfo.setFont(textFont);
				sourceInfo.setForeground(fontColor);
			}
			/*
			shrinkSizes = getShrinkSizes();
			DefaultComboBoxModel model = new DefaultComboBoxModel(shrinkSizes);
			shrinkSizeSelection.setModel(model);
			shrinkSizeSelection.setSelectedIndex(0);
			*/
		}
		// function to generate string of image information
		public String getImageAndROIInfo () {
			String imgInfo = "Target Image: ";
			String roiInfo = "Shrinked ROIs: ";
			if (targetImage==null)	imgInfo += "not set";
			else {
				String imgTitle = " " + wrapString(targetImage.getTitle(), lineWidth, 1);
				String imgSzie = " size: "
						+ new DecimalFormat(".#").format(targetImage.getSizeInBytes()/1048576)
						+ "MB (" + String.valueOf(targetImage.getBitDepth()) + " bit)";
				int[] dim = targetImage.getDimensions();
				String imgDimension = " X:" + String.valueOf(dim[0])
								  + ", Y:" + String.valueOf(dim[1])
								  + ", Z:" + String.valueOf(dim[3]);
				String imgBinary = targetImage.getProcessor().isBinary()?"Image is binary":"Image is not binary";
				String imgRoi = targetImage.getRoi()==null?"Image does not have active ROI.":"Image has active ROI.";
				imgInfo += imgTitle + "\n" + imgSzie + "\n" + imgDimension + "\n" + imgBinary + "\n" + imgRoi;
			}
			if (shrinkedRois==null || shrinkedRois.length==0) roiInfo += "empty";
			else roiInfo += "loaded";
			return (imgInfo + "\n\n" + roiInfo);
		}
		// function to wrap string after certain length for display in text area
		public String wrapString(
				String inputLongString,
				int wrapLength,
				int indent
				) {
			String wrappedString = ""; String indentStr = "";
			for (int i=0; i<indent; i++)
				indentStr += " ";
			for (int i=0; i<inputLongString.length(); i++) {
				if (i!=0 && i%lineWidth==0)	wrappedString += ("\n"+indentStr);
				wrappedString += inputLongString.charAt(i);
			}
			return wrappedString;
		}
		
		// function to group loaded ROIs into different shrink sizes
		public String[] getShrinkSizes () {
			if (shrinkedRois == null) return null;
			ArrayList<String> sizeList = new ArrayList<String>();
			for (Roi roi : shrinkedRois) {
				String roiShrinkSize = getShrinkSizeFromRoi(roi);
				if (!sizeList.contains(roiShrinkSize))
					sizeList.add(roiShrinkSize);
			}
			return sizeList.toArray(new String[sizeList.size()]);
		}
		// function to extract roi shrink string from roi label
		public String getShrinkSizeFromRoi (Roi roi) {
			String roiName = roi.getName();
			String sizeString = roiName.substring(roiName.indexOf("-shrink:")+8, roiName.length());
			return sizeString;
		}
		public void batchShrink () {
			// make use of scijava parameter persistence storage	
			DefaultPrefService prefs = new DefaultPrefService();		
			loadImage = prefs.getBoolean(Boolean.class, "RSOM-BRS-loadImage", loadImage);
			imagePath = prefs.get(String.class, "RSOM-BRS-imagePath", imagePath);
			loadSelection3D = prefs.getBoolean(Boolean.class, "RSOM-BRS-loadSelection3D", loadSelection3D);
			roiPath = prefs.get(String.class, "RSOM-BRS-roiPath", roiPath);
			shrinkStep = prefs.getDouble(Double.class, "RSOM-BRS-shrinkStep", shrinkStep);
			saveDirPath = prefs.get(String.class, "RSOM-BRS-saveDirPath", saveDirPath);
			// generate dialog	
			GenericDialogPlus gd = new GenericDialogPlus("Vesselness filtering");
			gd.addImageChoice("active image list", "");
			gd.addCheckbox("load image from disk", loadImage);
			gd.addFileField("path", imagePath);
			gd.addCheckbox("use current Roi Manager", !loadSelection3D);
			gd.addFileField("selection 3D", roiPath);
			gd.addNumericField("shrink step", shrinkStep, 1, 3, "µm");
			gd.addDirectoryField("save to folder", saveDirPath);
			gd.showDialog();
			if (gd.wasCanceled()) return;
			
			ImagePlus activeImage = null;
			try {
				activeImage = gd.getNextImage();
			} catch (ArrayIndexOutOfBoundsException e) {}
			loadImage = gd.getNextBoolean();
			imagePath = gd.getNextString();
			loadSelection3D = !gd.getNextBoolean();
			roiPath = gd.getNextString();
			shrinkStep = gd.getNextNumber();
			saveDirPath = gd.getNextString();
			// check input image and shrink parameter
			ImagePlus inputImage = loadImage ? IJ.openImage(imagePath) : activeImage;
			if (inputImage==null) return;
			String calUnit = inputImage.getCalibration().getUnit().toLowerCase();
			if (calUnit.equals("micron") || calUnit.equals("um") || calUnit.equals("µm"))
				pixelSize = inputImage.getCalibration().pixelWidth;
			else
				pixelSize = IJ.getNumber("pixel size (µm) of image: " + inputImage.getTitle(), 20.0);
			if (pixelSize==Integer.MIN_VALUE) return;
			int shrinkStepInPixel = (int)Math.round(shrinkStep/pixelSize);
			if (shrinkStepInPixel<1) return;
			targetImage = inputImage; targetImage.show();
			// parse input ROI file (or active ROI Manager) to ROI array
			currentRm = RoiManager.getInstance();
			if (!loadSelection3D) {
				if (currentRm==null || currentRm.getCount()==0) return;
			}
			
			
			if (currentRm!=null) rmOrigRois = currentRm.getRoisAsArray();
			else currentRm = new RoiManager();
			currentRm.setVisible(false);
			
			if (loadSelection3D) {
				currentRm.reset(); 
				currentRm.runCommand("Open", roiPath);
			}
			initialRois = currentRm.getRoisAsArray();

			int nROI = currentRm.getCount();
			for (int i=0; i<nROI; i++) {
				currentRm.select(targetImage, i);
				initialRois[i].setPosition(targetImage.getZ());
			}
			
			if (loadSelection3D && rmOrigRois!=null) {
				currentRm.reset(); 
				for (Roi roi : rmOrigRois)
					currentRm.addRoi(roi);
			}
			currentRm.setVisible(true);
			// put parameter into scijava persistence storage
			prefs.put(Boolean.class, "RSOM-BRS-loadImage", loadImage);
			prefs.put(String.class, "RSOM-BRS-imagePath", imagePath);
			prefs.put(Boolean.class, "RSOM-BRS-loadSelection3D", loadSelection3D);
			prefs.put(String.class, "RSOM-BRS-roiPath", roiPath);
			prefs.put(Double.class, "RSOM-BRS-shrinkStep", shrinkStep);
			prefs.put(String.class, "RSOM-BRS-saveDirPath", saveDirPath);
			
			// shrink the input ROIs to ROI array shrinkedRois
			shrinkedRois = shrink3DRoi(targetImage, initialRois, shrinkStep, pixelSize, saveDirPath);
			shrinkSizes = getShrinkSizes();
			if (shrinkSizes!=null && shrinkSizes.length!=0) {
				currentRm.setVisible(false);
				currentRm.reset();
				for (int i=0; i<shrinkedRois.length; i++) {
					currentRm.add(shrinkedRois[i], i);
					currentRm.rename(currentRm.getCount()-1, shrinkedRois[i].getName());
				}
				currentRm.runCommand("Save", saveDirPath+File.separator+"shrinked 3D selection.zip");
				// ugly way to load the shrinked 3D selection (We have a ROI name issue with direct load from shrinkedRois and shrinkSizes!!!)
				loadBatchRoi(saveDirPath+File.separator+"shrinked 3D selection.zip");
				// update shrink size list
				/*
				DefaultComboBoxModel model = new DefaultComboBoxModel(shrinkSizes);
				shrinkSizeSelection.setModel(model);
				shrinkSizeSelection.setSelectedIndex(0);
				selectedSize = shrinkSizes[0];
				
				// restore original ROI Manager
				currentRm.reset();
				for (Roi roi : rmOrigRois)
					currentRm.addRoi(roi);
				*/
				currentRm.setVisible(true);
				
	    	}
			refresh();
		}
		
		// shrink 3D ROI
		public Roi[] shrink3DRoi (
				ImagePlus inputImp,
				Roi[] inputRois, // initial 3D ROIs, sorted by slices;
				double shrinkStep,	// shrink step in micron
				double pixelSize,
				String saveDir
				) {
			if (inputImp==null || inputImp.getStackSize()<=1 || !inputImp.getProcessor().isBinary()) {
				IJ.error("Input image wrong: it has to be a binary stack");
				return null;
			}
			int step = (int)Math.round(shrinkStep/pixelSize);
			if (step<1) {
				IJ.error("Shrink step too small: less than 1 pixel!");
				return null;
			}
			ImagePlus imp = inputImp.duplicate();
			imp.setCalibration(null);
			ResultsTable rt = new ResultsTable();
			double vxSize = pixelSize*pixelSize*4;	// !!! use 20x20x4 µm3 as voxel size
			double numPixelSelection = 0;
			double numPixelVessel = 0;
			for (Roi r : inputRois) {
				//System.out.println("debug: batch shrink input roi position: 482" + r.getPosition());
				r.setName(r.getName().split("-")[0] + "-shrink:0 µm");
				imp.setPosition(r.getCPosition(), r.getZPosition(), r.getTPosition());
				imp.setRoi(r);
				numPixelSelection += imp.getProcessor().getStats().area;
				numPixelVessel += (imp.getProcessor().getStats().mean * imp.getProcessor().getStats().area / 255);
			}
			
			rt.incrementCounter();
			rt.addValue("shrink size (µm)", 0);
			rt.addValue("selection volume (µm3)",numPixelSelection*vxSize);
			rt.addValue("vessel volume (µm3)", numPixelVessel*vxSize);
			rt.addValue("vessel fraction (%)", 100*numPixelVessel/numPixelSelection);
			
			ArrayList<Roi> shrinkedRois = new ArrayList<Roi>(Arrays.asList(inputRois));
			@SuppressWarnings("unchecked")
			ArrayList<Roi> bufferList = (ArrayList<Roi>) shrinkedRois.clone();
			
			int iter = 0;
			while (bufferList.size()>0) {
				numPixelSelection = 0; numPixelVessel = 0;
				String shrinkLabel = "-shrink:" + String.valueOf(++iter*pixelSize) + " µm";
				for (int i=0; i<step; i++) 
					bufferList.remove(bufferList.size()-1);
				if (bufferList.size()==0) break;
				for (int i=0; i<step; i++) 
					bufferList.remove(0);
				
				int nROI = bufferList.size();
				for (int j=nROI; j>0; j--) {
					Roi r = bufferList.get(j-1);
					//System.out.println("debug: batch shrink input roi position 512:" + r.getPosition());
					//String roiName = r.getName().substring(0, r.getName().indexOf("-shrink:"));
					// !!! HERE!!!
					String roiName = r.getName().split("-")[0];
					double width = r.getFloatWidth();
					Roi r2 = RoiEnlarger.enlarge(r, -step);
					if (r2.getFloatWidth() == width) {
						bufferList.remove(r);
						continue;
					}
					r2.setPosition(r.getPosition());
					r2.setName(roiName + shrinkLabel);
					bufferList.set(j-1, r2);
					imp.setPosition(r2.getCPosition(), r2.getZPosition(), r2.getTPosition());
					imp.setRoi(r2);
					numPixelSelection += imp.getProcessor().getStats().area;
					numPixelVessel += (imp.getProcessor().getStats().mean * imp.getProcessor().getStats().area / 255);
				}
				shrinkedRois.addAll(bufferList);
				//if (bufferList.size()==0) break;
				rt.incrementCounter();
				rt.addValue("shrink size (µm)", iter*pixelSize);
				rt.addValue("selection volume (µm3)", numPixelSelection*vxSize);
				rt.addValue("vessel volume (µm3)", numPixelVessel*vxSize);
				rt.addValue("vessel fraction (%)", numPixelSelection==0?0:100*numPixelVessel/numPixelSelection);
			}
			rt.save(saveDir+File.separator+"batch shrink result.csv");
			rt.show("Batch Shrink Result");
			imp.changes = false; imp.close(); System.gc();
			Roi[] rois = shrinkedRois.toArray(new Roi[shrinkedRois.size()]); 
			return rois;
		}
		// function to load batch ROI
		public void loadBatchRoi (String path) {
			if (path==null) {
				OpenDialog od = new OpenDialog("Load Batch Shrink ROIs...");
		    	path = od.getPath();
			}
			if (path == null || !path.toLowerCase().endsWith(".zip"))
				return;
			shrinkSizes = null;
	    	Roi[] rmRois = currentRm.getRoisAsArray();
	    	currentRm.setVisible(false);
	    	currentRm.reset();
	    	currentRm.runCommand("Open", path);
	    	shrinkedRois = currentRm.getRoisAsArray();
	    	currentRm.reset();
	    	for (int i=0; i<rmRois.length; i++) {
	    		currentRm.addRoi(rmRois[i]);
			}
	    	currentRm.setVisible(true);
	    	shrinkSizes = getShrinkSizes();
	    	if (shrinkSizes==null || shrinkSizes.length==0) return;
			DefaultComboBoxModel<String> model = new DefaultComboBoxModel<String>(shrinkSizes);
			shrinkSizeSelection.setModel(model);
			shrinkSizeSelection.setSelectedIndex(0);
			selectedSize = shrinkSizes[0];
			refresh();
		}
		// function to display specific ROI
		public void showRoi () {
			//currentRm.setVisible(false);
	    	currentRm.reset();
	    	for (Roi r : shrinkedRois) {
	    		String roiName = r.getName();
	    		if (roiName.substring(roiName.indexOf("-shrink:")+8, roiName.length()).equals(selectedSize))
	    				currentRm.add(r, currentRm.getCount());
	    	}
	    	//currentRm.setVisible(true);
		}
		// function to display the overlay image
		public void showOverlay () {
			if (targetImage==null || shrinkedRois==null || shrinkedRois.length==0) return;
			
			// make use of scijava parameter persistence storage, generate dialog, and save back to storage
			DefaultPrefService prefs = new DefaultPrefService();
			idxOptionColor = prefs.getInt(Integer.class, "RSOM-BRS-idxOptionColor", idxOptionColor);
			idxOption3D = prefs.getInt(Integer.class, "RSOM-BRS-idxOption3D", idxOption3D);
			roiDoCalibrate = prefs.getBoolean(Boolean.class, "RSOM-BRS-roiDoCalibrate", roiDoCalibrate);
			GenericDialog gd = new GenericDialog("Configure Shrink ROI Display");
			gd.addChoice("color code", optionColor, optionColor[idxOptionColor]);
			gd.addChoice("3D option", option3D, option3D[idxOption3D]);
			gd.addCheckbox("calibrate ", roiDoCalibrate);
			gd.showDialog();
			if (gd.wasCanceled()) return;
			idxOptionColor = gd.getNextChoiceIndex();
			idxOption3D = gd.getNextChoiceIndex();
			roiDoCalibrate = gd.getNextBoolean();
			prefs.put(Integer.class, "RSOM-BRS-idxOptionColor", idxOptionColor);
			prefs.put(Integer.class, "RSOM-BRS-idxOption3D", idxOption3D);
			prefs.put(Boolean.class, "RSOM-BRS-roiDoCalibrate", roiDoCalibrate);
			
			
			ImagePlus[] overlayImage= new ImagePlus[2];
			
			overlayImage[0] = targetImage.duplicate();
			
			overlayImage[0].show(); overlayImage[0].getWindow().setVisible(false);
			overlayImage[0].setLut(LUT.createLutFromColor(Color.RED));
			//vesselMaskImage = overlayImage[0];
			//if (roiDoCalibrate) vesselMaskImage.setCalibration(targetImage.getCalibration());
			//vesselMaskImage.show(); vesselMaskImage.getWindow().setVisible(false);
			
			overlayImage[1] = NewImage.createByteImage("", targetImage.getWidth(),
					targetImage.getHeight(), targetImage.getStackSize(), NewImage.FILL_BLACK);
			
			ImageProcessor ip = overlayImage[1].getProcessor();
			overlayImage[1].show(); overlayImage[1].getWindow().setVisible(false);
			// break parts of the shrink labels in ROI name
			String srk1 = "-shrink:"; String srk2 = " µm"; String srk3 = "";
			int fillColor = 0;
			for (Roi r : shrinkedRois) {
				String shrinkSize = r.getName().substring(r.getName().indexOf(srk1)+8, r.getName().indexOf(srk2));
				if (!shrinkSize.equals(srk3)) {
					srk3 = shrinkSize;
					ip.setColor(++fillColor);
				}
				overlayImage[1].setPosition(r.getCPosition(), r.getZPosition(), r.getTPosition());
				ip.fill(r);
			}
			IJ.run(overlayImage[1], optionColor[idxOptionColor], "");
			ip.setMinAndMax(0, fillColor);
			//shrinkedRoiImage = overlayImage[1];
			//if (roiDoCalibrate) shrinkedRoiImage.setCalibration(targetImage.getCalibration());
			//shrinkedRoiImage.show(); shrinkedRoiImage.getWindow().setVisible(false);

			overlayComposite = RGBStackMerge.mergeChannels(overlayImage, false);
			System.gc();
			overlayComposite.setTitle(targetImage.getTitle() + " shrinked ROI Overlay");
			if (roiDoCalibrate) overlayComposite.setCalibration(targetImage.getCalibration());
			overlayComposite.show();
			
			if (idxOption3D!=0) {
				new show3DThread().start();
			}
		}
		
		public void do3DRendering () {
			if (overlayComposite==null) return;
			Color3f cf = new Color3f(300,300,300);
			boolean[] channels = new boolean[] {true,true,true};
			Image3DUniverse univ = new Image3DUniverse();
			univ.show();
			if (idxOption3D==1) {
				univ.addVoltex(overlayComposite,cf,overlayComposite.getTitle()+" 3D rendering",1 ,channels, 2);
			} else if (idxOption3D==2) {
				univ.addOrthoslice(overlayComposite,cf,overlayComposite.getTitle()+" 3D rendering",1 ,channels, 2);
			}
		}
		public class show3DThread extends Thread {
			public void run() {
				do3DRendering();
			}
		}
		
		public void convertToMask () {
			if (targetImage==null || shrinkedRois==null || shrinkedRois.length==0) return;
			
			int width = targetImage.getWidth();
			int height = targetImage.getHeight();
			int numZ = targetImage.getStackSize();
			
			selectedSize = (String) shrinkSizeSelection.getSelectedItem();
			
			ImagePlus mask = NewImage.createByteImage("mask shrink "+selectedSize, width, height, numZ, NewImage.FILL_BLACK);
			ImageProcessor ip = mask.getProcessor();
			ip.setColor(Color.WHITE);
			Roi[] rmRois = currentRm.getRoisAsArray();
	    	for (Roi r : rmRois) {
	    		mask.setZ(r.getZPosition());
	    		ip.fill(r);
	    	}
	    	mask.changes = true;
	    	mask.show();
	    	mask.setSlice(targetImage.getCurrentSlice());
		}
		
		// function to display specific ROI
		public void restoreManager () {
			//currentRm.setVisible(false);
	    	currentRm.reset();
	    	for (Roi r : rmOrigRois) {
	    		currentRm.addRoi(r);
	    	}
	    	//currentRm.setVisible(true);
		}
		
	@Override
	public void run(String arg) {
		
		targetImage = WindowManager.getCurrentImage();
		currentRm = RoiManager.getInstance();
		if (currentRm!=null) rmOrigRois = currentRm.getRoisAsArray();
		
		pf = new PlugInFrame("3D ROI Shrink");
		pf.setLayout(new BoxLayout(pf, BoxLayout.Y_AXIS));

		addPanelToFrame(pf);
		
		pf.pack();
		pf.setSize(300, 550);
		pf.setVisible(true);
		pf.setLocationRelativeTo(null);
		//pf.setResizable(false);
		GUI.center(pf);	
	}

}
