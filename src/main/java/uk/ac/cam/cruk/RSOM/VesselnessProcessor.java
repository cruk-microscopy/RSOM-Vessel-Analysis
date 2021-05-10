package uk.ac.cam.cruk.RSOM;

import features.TubenessProcessor;

public class VesselnessProcessor extends TubenessProcessor {
	
	// input data dynamic range, for the purpose of normalization
	private double normFactor = 255.0d;	// 8-bit binary image
	// Vesselness filter method
	private int method = 0;
	
	// Constant Field Values
	public static final String[] methodNames = {"Frangi", "Sato-simple", "Sato-full"};
	public static final int Sato_simple = 1;
	public static final int Sato_full = 2;
	public static final int Frangi = 0;
	
	// Additional parameters for different methods:
	private float Frangi_a = 0.5f;
	private float Frangi_b = 0.5f;
	private float Frangi_c = 0.5f;
	private float Frangi_Sr = 0.8646647167633873f;
	private float Sato_alpha = 1.0f;
	
	// constructor, from Tubeness Processor
	public VesselnessProcessor (boolean useCalibration) {
		super(useCalibration);
	}
	public VesselnessProcessor (double sigma, boolean useCalibration) {
		super(sigma, useCalibration);
	}
	
	// overwrite functions
	@Override
	public float measureFromEvalues3D( float [] evalues ) {

		/* If either of the two principle eigenvalues is
		   positive then the curvature is in the wrong
		   direction - towards higher instensities rather than
		   lower. 
		
		   evalues[0] > evalues[1] > evalues[2]
		   lambda1 > lambda2 > lambda3
		*/
		if ( method == Sato_simple )
			return (float) (getSatoSimple ( evalues ) / normFactor);
		else if ( method == Sato_full )
			return (float) (getSatoFull ( evalues ) / normFactor);
		else if ( method == Frangi )
			return (float) (getFrangi ( evalues ) / normFactor);
		else
			return Float.NaN;

		
		/*	
		float v1 = evalues[0]; float v2 = evalues[1]; float v3 = evalues[2];
		final float alpha = 1.0f;
		if (v1 <= 0) {
			return (float) Math.sqrt( -v3 * (v1 - v2) );
		} else if (v2<0 && v1<(-v2/alpha) ) {
			return (float) Math.sqrt( -v3 * (-alpha*v1 - v2) );
		} else {
			return 0;
		}
				
			
		if ((evalues[1] >= 0) || (evalues[2] >= 0))
			return 0;
		else
			return (float)Math.sqrt(evalues[2] * evalues[1]);
		*/
	}
	
	public void setNormFactor (double normFactor) { this.normFactor = normFactor; }
	/*
	 * 	Vesselness Formulas based on Hessian eigenvalue analysis:
	 * 		1, Frangi:
	 * 		2, Sato simple:
	 * 		3, Sato full:
	 * 
	 */
	// set sensitive parameters of different method, besides Hessian eigenvalues
	public void setMethod (int method) { this.method = method; }
	public void setFrangi_a (float a) { this.Frangi_a = a; }
	public void setFrangi_b (float b) { this.Frangi_b = b; }
	public void setFrangi_c (float c) { this.Frangi_c = c; }
	public void setFrangi_abc (float[] abc) {
		if ( abc.length!=3 ) return;
		this.Frangi_a = abc[0]; this.Frangi_b = abc[1]; this.Frangi_c = abc[2]; 
	}
	public void setSatoAlpha (float alpha) { this.Sato_alpha = alpha; }
	
	
	/* Frangi A.F., Niessen W.J., Vincken K.L., Viergever M.A. (1998) 
	 * 		Multiscale vessel enhancement filtering. 
	 * 		In: Wells W.M., Colchester A., Delp S. (eds) Medical Image Computing and Computer-Assisted Intervention — MICCAI’98. MICCAI 1998. Lecture Notes in Computer Science, vol 1496. Springer, Berlin, Heidelberg
	 */
	private float getFrangi ( float[] evalues) {	// eq.13
		return getFrangi (evalues, this.Frangi_a, this.Frangi_b, this.Frangi_c);
	}
	private float getFrangi ( float[] evalues, float c) {
		return getFrangi (evalues, this.Frangi_a, this.Frangi_b, c);
	}
	private float getFrangi ( float [] evalues, float a, float b, float c ) {
		//if (evalues==null || evalues.length!=3) return Float.NaN;
		float v1 = evalues[0]; float v2 = evalues[1]; float v3 = evalues[2];
		if ( (v2 >= 0) || (v3 >= 0)) {
			return 0;
		} else {
			// Frangi formula:
			float Rb = (float) ( Math.abs( v1 ) / (Math.sqrt( Math.abs( v2 * v3 ) )));	// eq.10
			float Ra = (float) Math.abs( v2 / v3 );		// eq.11
			float S = (float) Math.sqrt( (v1*v1) + (v2*v2) + (v3*v3) );	// eq.12;	L2 Norm

			float e1 = -1 * Ra * Ra / (2*a*a);	// eq.13
			float e2 = -1 * Rb * Rb / (2*b*b);	// eq.13
			float e3 = -1 * S * S / (2*c*c);	// eq.13
			
			return (float) ( (1 - Math.exp(e1)) * (Math.exp(e2)) * (1 - Math.exp(e3)) );
			//return	(float) ( (1 - Math.exp(e1))  * Frangi_Sr );
		}
	}
	
	
	/*
	 *  Sato Y, Nakajima S, Shiraga N, et al. (1998)
	 *  	Three-dimensional multi-scale line filter for segmentation and visualization of curvilinear structures in medical images. 
	 *  	Med Image Anal. 1998;2(2):143‐168. doi:10.1016/s1361-8415(98)80009-1
	 */
	private float getSatoSimple ( float [] evalues ) {	// eq.9
		//if (evalues==null || evalues.length!=3) return Float.NaN;
		float v1 = evalues[0]; float v2 = evalues[1]; float v3 = evalues[2];
		if ( (v2 >= 0) || (v3 >= 0))
			return 0;
		else
			return (float)Math.sqrt( v2 * v3 );
	}
	
	
	private float getSatoFull ( float[] evalues ) {	// eq.18
		return getSatoFull (evalues, this.Sato_alpha);
	}
	private float getSatoFull ( float [] evalues , float alpha) {
		//if (evalues==null || evalues.length!=3) return Float.NaN;
		float v1 = evalues[0]; float v2 = evalues[1]; float v3 = evalues[2];
		if (v1 <= 0) {
			return (float) Math.sqrt( -v3 * ( v1 - v2 ) );
		} else if ( (v2 < 0) && (v1 < ( -v2 / alpha )) ) {
			return (float) Math.sqrt( -v3 * (-alpha * v1 - v2) );
		} else {
			return 0;
		}
	}
	
}
