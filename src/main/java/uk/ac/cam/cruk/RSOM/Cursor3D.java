package uk.ac.cam.cruk.RSOM;

public class Cursor3D {
	protected int x = 0;
	protected int y = 0;
	protected int z = 0;
	public Cursor3D(int x, int y, int z)
	{
		this.x = x;
		this.y = y;
		this.z = z;
	}
	public void set(int x, int y, int z)
	{
		this.x = x;
		this.y = y;
		this.z = z;
	}
	public int getX()
	{
		return x;
	}
	public int getY()
	{
		return y;
	}
	public int getZ()
	{
		return z;
	}
	@Override
	public boolean equals( Object other )
	{
	    if (other == null) return false;
	    if (other == this) return true;
	    if ( !( other instanceof Cursor3D ) )
	    	return false;
	    Cursor3D c = (Cursor3D) other;
	    return c.x == this.x && c.y == this.y && c.z == this.z;
	}
}