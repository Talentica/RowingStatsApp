package com.talentica.rowingapp.common;

import android.location.Location;

import com.talentica.rowingapp.common.data.DataIdx;
import com.talentica.rowingapp.common.data.way.DistanceResolver;

/**
 * resolves GPS location distance diffs
 *
 */
public class AndroidLocationDistanceResolver implements DistanceResolver {

	@Override
	public float calcDistance(double[] loc1, double[] loc2) {
		float[] result = {0};
		Location.distanceBetween(loc1[DataIdx.GPS_LAT], loc1[DataIdx.GPS_LONG],
								loc2[DataIdx.GPS_LAT], loc2[DataIdx.GPS_LONG],
								result);
		return result[0];
	}

}
