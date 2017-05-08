package com.talentica.rowingapp.common.data;

/**
 * Static array index ID holder.
 * The index IDs can help keeping the code more readable by using e.g.
 * <code>values[DataIdx.ACCEL_Y]</code> rather than <code>values[1]</code> inside the code
 */
public final class DataIdx {
	public static final int ACCEL_X = 0;
	public static final int ACCEL_Y = 1;
	public static final int ACCEL_Z = 2;
	public static final int ORIENT_PITCH = 1;
	public static final int ORIENT_ROLL = 2;
	public static final int GPS_LAT = 0;
	public static final int GPS_LONG = 1;
	public static final int GPS_ALT = 2;
	public static final int GPS_SPEED = 3;
	public static final int GPS_BEARING = 4;
	public static final int GPS_ACCURACY = 5;
	public static final int GPS_ITEM_COUNT_ = 6;	
}
