# RSOM-Vessel-Analysis
A Fiji plugin to analyze optoacoustic images of blood vessel

To download the compiled plugin, go to target and locate the latest jar file:
RSOM_Vessel_Analysis-#.jar
Download this file and add to plugin folder of Fiji



<html>
				 <h2>RSOM blood vessel analysis (ImageJ plugin)</h2>
				 + version: 1.6.2<br>
				 + date: 2019.04.09<br>
				 + author: Ziqiang Huang (Ziqiang.Huang@cruk.cam.ac.uk)<br><br>
				 +<h3>Usage:</h3>
				 +<&nbsp>Choose RSOM image stack from disk or from active<br>
				 + image lists in Fiji.<br>
				 + <&nbsp>Both tiff stack and image sequence are accepted. Image<br>
				 + sequence is recongnized automatically.<br>
				 +<br><&nbsp>Thresholding to get a binary mask of the stack.<br>
				 + <&nbsp>If choose manual thresholding, a dialog box going to<br>
				 + pop up and ask for user to select the thresholding bounds.<br>
				 + <&nbsp>Always use the stack histogram to calculate threshold.<br>
				 +<br><&nbsp>User can define a 3D selection (ROI), or load a pre-<br>
				 + existing ROI file from disk.<br>
				 + <&nbsp>If the ROI file was created by previous plugin execution,<br>
				 + then the file name should be image file name followed by<br>
				 + \_selection3D.zip\, and it should be a ROIset file that can<br>
				 + be loaded by ROI Manager in Fiji.<br>
				 +<br><&nbsp>Computation results, intermediate image stacks, as well<br>
				 + as an execution log will be saved based on user choices.<br>
				 + <&nbsp>The results will be stored to a folder nested next to the<br>
				 + input image file.<br>
				 + <&nbsp>Be aware if certain results are not ticked, the execution<br>
				 + might be skipped.<br>
				 +<br><&nbsp>Known issue: diameter map only take isotropic voxel as input.<br>
				 +<br><&nbsp>Known issue: diameter map does not return system memory (4 times the input file).<br>;
