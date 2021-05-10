package uk.ac.cam.cruk.RSOM;

import java.awt.event.*;
import java.awt.*;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.io.File;
import java.io.FilenameFilter;
import java.net.URL;
import java.util.ArrayList;

import javax.swing.event.*;
import ij.IJ;
import ij.plugin.PlugIn;


public class FileList implements PlugIn{

	JLabel label = new JLabel("test:");;

	//private static String initPath = "I:/core/light_microscopy/data/group_folders/Ziqiang/ZH_Emma/New images";
	private static String initPath = IJ.getDirectory("home");   
	private static JTextField txtPath;
	
	
	private JButton btnBrowse;
	private JButton btnRefresh;
	
	private static String keyword = "";
	private static JTextField filter;
	
	private JScrollPane scrollPane;
	
	private JTable getTable(
			String filePath,
			String includeWord
			) {
		FilenameFilter keywordFilter = new FilenameFilter() {
			public boolean accept(File dir, String name) {
						String lowercaseName = name.toLowerCase();
						if (lowercaseName.contains(includeWord)) {
							return true;
						} else {
							return false;
						}
					}
			};
		
		File folder = new File(filePath);
		String[] fileList = folder.list(keywordFilter);
		int numFiles = fileList.length;
		//String[] columns = ["file", "type"];
		String[] columns = {"file"};
		Object[][] data = new Object[numFiles][1];
		for (int i = 0; i<numFiles; i++) {
			data[i][0] = fileList[i];
		}
		return new JTable(data, columns);
	}

	public void updateContent() {
		String keyword = filter.getText();
		File folder = new File(txtPath.getText());
	
		FilenameFilter keywordFilter = new FilenameFilter() {
			public boolean accept(File dir, String name) {
						String lowercaseName = name.toLowerCase();
						if (lowercaseName.contains(keyword)) {
							return true;
						} else {
							return false;
						}
					}
		};
	
		String[] fileList = folder.list(keywordFilter);
		int numFiles = fileList.length;
		String[] columns = {"file"};
		Object[][] data = new Object[numFiles][1];
		for (int i = 0; i<numFiles; i++) {
			data[i][0] = fileList[i];
		}
		JTable table = new JTable(data, columns);
		scrollPane.setViewportView(table);
	}
		
	

	
	
	@Override
    public void run(String arg0) {
		try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        	} catch(Exception weTried) {
        }
		
		
		JFrame f = new JFrame("test");
        f.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        
        
        
		
		JLabel label = new JLabel("root directory:");;

		//String initPath = "I:/core/light_microscopy/data/group_folders/Ziqiang/ZH_Emma/New images";
		//firstPanel.setPreferredSize(new Dimension(400, 23));    
		txtPath = new JTextField(initPath);
		txtPath.setColumns(10);


		scrollPane = new JScrollPane(getTable(initPath, keyword));
		
		// configure browse button
		btnBrowse = new JButton("Browse");
		btnBrowse.addActionListener(new ActionListener() {
		      public void actionPerformed(ActionEvent e) {
		        JFileChooser fileChooser = new JFileChooser(initPath);
		        // For Directory
		        //fileChooser.setInitialDirectory(new File(initPath));
		        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		        // For File
		        //fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		        fileChooser.setAcceptAllFileFilterUsed(false);
		        int rVal = fileChooser.showOpenDialog(null);
		        if (rVal == JFileChooser.APPROVE_OPTION) {
		        	initPath = fileChooser.getSelectedFile().toString();
		          txtPath.setText(initPath);
		        } else {
		        	return;
		        }
				File folder = fileChooser.getSelectedFile();
				String[] fileList = folder.list();
				int numFiles = fileList.length;
				String[] columns = {"file"};
				Object[][] data = new Object[numFiles][1];
			    for (int i = 0; i<numFiles; i++) {
					data[i][0] = fileList[i];
				}
			    JTable table = new JTable(data, columns);
				scrollPane.setViewportView(table);
		      }
		});
		
		// configure refresh button
		btnRefresh = new JButton("Refresh");
		btnRefresh.addActionListener(new ActionListener() {
	      public void actionPerformed(ActionEvent e) {
	        keyword = filter.getText();
			scrollPane.setViewportView(getTable(initPath, keyword));
	      }
	    });
		
		// configure text field
		filter = new JTextField(keyword);
		filter.setColumns(5);
		
		JScrollPane scrollPane = new JScrollPane(getTable(initPath, keyword));


		filter.getDocument().addDocumentListener(new DocumentListener() {
			
		    public void insertUpdate(DocumentEvent e) {
		    	keyword = filter.getText();
				scrollPane.setViewportView(getTable(initPath, keyword));
		    }

		    public void removeUpdate(DocumentEvent e) {
		    	keyword = filter.getText();
				scrollPane.setViewportView(getTable(initPath, keyword));
		    }

		    public void changedUpdate(DocumentEvent e) {
				keyword = filter.getText();
				scrollPane.setViewportView(getTable(initPath, keyword));	
			}
		});
		
		
		JFrame frame = new JFrame();
		GroupLayout layout = new GroupLayout(frame.getContentPane());
		frame.getContentPane().setLayout(layout);
		layout.setAutoCreateGaps(true);
		layout.setAutoCreateContainerGaps(true);

		layout.setHorizontalGroup(layout.createSequentialGroup()
			.addComponent(label)
			.addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
				.addComponent(txtPath)
				.addGroup(layout.createSequentialGroup()
					.addComponent(btnBrowse)
					.addComponent(btnRefresh))
				.addComponent(filter)	
				.addComponent(scrollPane))
			.addComponent(label));		
		layout.linkSize(SwingConstants.HORIZONTAL, btnBrowse, btnRefresh);

		layout.setVerticalGroup(layout.createSequentialGroup()
			.addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
				.addComponent(label)
				.addComponent(txtPath)
				.addComponent(label))	
			.addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
		         .addComponent(btnBrowse)
		         .addComponent(btnRefresh))
		    .addComponent(filter)     
		    .addComponent(scrollPane));

			
		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		frame.pack();
		frame.setVisible(true);
		
    }
    
	public static String getRootPath () {
		return initPath;
	}
	
	public static String getFilter () {
		return keyword;
	}
	
    public static void main(String[] args) {
    	FileList f = new FileList();
    	f.run(null);
               
    }
    
}
