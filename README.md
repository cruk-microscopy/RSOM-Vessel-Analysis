# RSOM-Vessel-Analysis
A Fiji plugin to analyze optoacoustic images of blood vessel

To download the compiled plugin, go to target and locate the latest jar file:
RSOM_Vessel_Analysis-#.jar
Download this file and add to plugin folder of Fiji




html
				 h2RSOM blood vessel analysis (ImageJ plugin)/h2
				  version: 1.6.2br
				  date: 2019.04.09br
				  author: Ziqiang Huang (Ziqiang.Huang@cruk.cam.ac.uk)brbr
				 h3Usage:/h3
				 &nbspChoose RSOM image stack from disk or from activebr
				  image lists in Fiji.br
				  &nbspBoth tiff stack and image sequence are accepted. Imagebr
				  sequence is recongnized automatically.br
				 br&nbspThresholding to get a binary mask of the stack.br
				  &nbspIf choose manual thresholding, a dialog box going tobr
				  pop up and ask for user to select the thresholding bounds.br
				  &nbspAlways use the stack histogram to calculate threshold.br
				 br&nbspUser can define a 3D selection (ROI), or load a pre-br
				  existing ROI file from disk.br
				  &nbspIf the ROI file was created by previous plugin execution,br
				  then the file name should be image file name followed bybr
				  _selection3D.zip, and it should be a ROIset file that canbr
				  be loaded by ROI Manager in Fiji.br
				 br&nbspComputation results, intermediate image stacks, as wellbr
				  as an execution log will be saved based on user choices.br
				  &nbspThe results will be stored to a folder nested next to thebr
				  input image file.br
				  &nbspBe aware if certain results are not ticked, the executionbr
				  might be skipped.br
				 br&nbspKnown issue: diameter map only take isotropic voxel as input.br
				 br&nbspKnown issue: diameter map does not return system memory (4 times the input file).br;
