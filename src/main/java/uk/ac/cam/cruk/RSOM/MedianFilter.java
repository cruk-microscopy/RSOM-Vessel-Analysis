package uk.ac.cam.cruk.RSOM;

import ij.ImagePlus;
import ij.process.ImageProcessor;
import ij.plugin.filter.RankFilters;

public class MedianFilter {

	protected int filterType = RankFilters.MEDIAN;
	protected double radius = 2.5;
	
	public ImagePlus medianFilter(
			ImageProcessor ip
            ) {
		RankFilters rf = new RankFilters();
		rf.rank(ip, radius, filterType);
		ImagePlus impFiltered = new ImagePlus("RSOM filtered", ip);
		return impFiltered;
	}
	
	public ImagePlus medianFilter(
			ImagePlus imp,
			double radius
            ) {
		RankFilters rf = new RankFilters();
		int z = imp.getNSlices();
		for (int i=0; i<z; i++) {
			imp.setSlice(i+1);
			rf.rank(imp.getProcessor(), radius, filterType);
		}
		//ImagePlus impFiltered = new ImagePlus("RSOM filtered", ip);
		return imp;
	}
	
	public ImagePlus medianFilter(
			ImageProcessor ip,
            double radius,
            int filterType) {
		RankFilters rf = new RankFilters();
		rf.rank(ip, radius, filterType);
		ImagePlus impFiltered = new ImagePlus("RSOM filtered", ip);
		return impFiltered;
	}
}
